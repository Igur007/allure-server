package qa.allure.service;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.qameta.allure.entity.ExecutorInfo;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import qa.allure.entity.ReportEntity;
import qa.allure.helper.AllureReportGenerator;
import qa.allure.helper.ServeRedirectHelper;
import qa.allure.properties.AllureProperties;
import qa.allure.repo.JpaReportRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Optional.ofNullable;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import static qa.allure.gui.DateTimeResolver.zeroZone;
import static qa.allure.helper.ExecutorCiPlugin.JSON_FILE_NAME;
import static qa.allure.helper.Util.join;

@Component
@Slf4j
@Transactional
public class JpaReportService {

    @Getter
    private final Path reportsDir;
    private final AllureProperties cfg;
    private final ObjectMapper objectMapper;
    private final AllureReportGenerator reportGenerator;
    private final ServeRedirectHelper redirection;
    private final JpaReportRepository repository;
    private final ResultService reportUnzipService;
    private static final String INDEX_HTML = "index.html";
    private final AtomicBoolean init = new AtomicBoolean();

    public JpaReportService(AllureProperties cfg,
                            ObjectMapper objectMapper,
                            JpaReportRepository repository,
                            AllureReportGenerator reportGenerator,
                            ServeRedirectHelper redirection
    ) {
        this.reportsDir = cfg.reports().dirPath();
        this.cfg = cfg;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.reportGenerator = reportGenerator;
        this.redirection = redirection;
        this.reportUnzipService = new ResultService(reportsDir);
    }

    @PostConstruct
    protected void initRedirection() {
        repository.findByActiveTrue().forEach(
            e -> redirection.mapRequestTo(join(cfg.reports().path(), e.getPath()), reportsDir.resolve(e.getUuid().toString()).toString())
        );
    }

    public Collection<ReportEntity> clearAllHistory() {
        final Collection<ReportEntity> entitiesActive = repository.findByActiveTrue();
        final Collection<ReportEntity> entitiesInactive = repository.deleteByActiveFalse();

        // delete active history
        entitiesActive
            .forEach(e -> deleteQuietly(reportsDir.resolve(e.getUuid().toString()).resolve("history").toFile()));

        // delete active history
        entitiesInactive
            .forEach(e -> deleteQuietly(reportsDir.resolve(e.getUuid().toString()).toFile()));

        return entitiesInactive;
    }

    public void internalDeleteByUUID(UUID uuid) throws IOException {
        repository.deleteById(uuid);
        FileUtils.deleteDirectory(reportsDir.resolve(uuid.toString()).toFile());
    }

    public Collection<ReportEntity> deleteAll() throws IOException {
        var res = getAll();
        repository.deleteAll();
        FileUtils.deleteDirectory(reportsDir.toFile());
        return res;
    }

    public Collection<ReportEntity> deleteAllOlderThanDate(LocalDateTime date) {
        final Collection<ReportEntity> res = repository.findAllByCreatedDateTimeIsBefore(date);
        res.forEach(e -> {
            repository.deleteById(e.getUuid());
            deleteQuietly(reportsDir.resolve(e.getUuid().toString()).toFile());
        });
        return res;
    }

    public Collection<ReportEntity> getAll() {
        return repository.findAll(Sort.by("createdDateTime").descending());
    }

    @SneakyThrows
    public ReportEntity uploadReport(@NonNull String reportPath,
                                     @NonNull InputStream archiveInputStream,
                                     ExecutorInfo executorInfo,
                                     String baseUrl) {

        // New report destination and entity
        final Path destination = reportUnzipService.unzipAndStore(archiveInputStream);
        final UUID uuid = UUID.fromString(destination.getFileName().toString());
        Preconditions.checkArgument(
            Files.list(destination).anyMatch(path -> path.endsWith(INDEX_HTML)),
            "Uploaded archive is not an Allure Report"
        );

        // Find prev report if present
        final Optional<ReportEntity> prevEntity = repository.findByPathOrderByCreatedDateTimeDesc(reportPath)
            .stream()
            .findFirst();

        // Add CI executor information
        var safeExecutorInfo = addExecutionInfo(
            destination,
            executorInfo,
            getExecutorReportUrl(executorInfo, baseUrl, uuid, INDEX_HTML),
            uuid
        );

        log.info("Report '{}' loaded", destination);

        // New report entity
        final ReportEntity newEntity = ReportEntity.builder()
            .uuid(uuid)
            .path(reportPath)
            .createdDateTime(LocalDateTime.now(zeroZone()))
            .url(safeExecutorInfo.getReportUrl())
            .level(prevEntity.map(e -> e.getLevel() + 1).orElse(0L))
            .active(true)
            .size(ReportEntity.sizeKB(destination))
            .buildUrl(
                // Взять Build Url
                ofNullable(safeExecutorInfo.getBuildUrl())
                    // Or Build Name
                    .or(() -> ofNullable(safeExecutorInfo.getBuildName()))
                    // Or Executor Name
                    .or(() -> ofNullable(safeExecutorInfo.getName()))
                    // Or Executor Type
                    .orElse(safeExecutorInfo.getType())
            )
            .build();

        // Add request mapping
        redirection.mapRequestTo(newEntity.getPath(), reportsDir.resolve(uuid.toString()).toString());

        // Persist
        handleMaxHistory(newEntity);
        repository.saveAndFlush(newEntity);

        // Disable prev report
        prevEntity.ifPresent(e -> e.setActive(false));

        return newEntity;
    }

    public ReportEntity generate(@NonNull String reportPath,
                                 @NonNull List<Path> resultDirs,
                                 boolean clearResults,
                                 ExecutorInfo executorInfo,
                                 String baseUrl
    ) throws IOException {
        // Preconditions
        Preconditions.checkArgument(!resultDirs.isEmpty());
        resultDirs.forEach(i -> Preconditions.checkArgument(Files.exists(i), "Result '%s' doesn't exist", i));

        // New report destination and entity
        final UUID uuid = UUID.randomUUID();

        // Find prev report if present
        final Optional<ReportEntity> prevEntity = repository.findByPathOrderByCreatedDateTimeDesc(reportPath)
            .stream()
            .findFirst();

        // New uuid directory
        final Path destination = reportsDir.resolve(uuid.toString());

        // Copy history from prev report
        final Optional<Path> historyO = prevEntity
            .flatMap(e -> copyHistory(reportsDir.resolve(e.getUuid().toString()), uuid.toString()))
            .or(Optional::empty);

        // Add CI executor information
        var safeExecutorInfo = addExecutionInfo(
            resultDirs.getFirst(),
            executorInfo,
            getExecutorReportUrl(executorInfo, baseUrl, uuid, INDEX_HTML),
            uuid
        );

        var reportUrl = getExecutorReportUrl(executorInfo, baseUrl, uuid, "/");

        try {
            // Add history to results if exists
            final List<Path> resultDirsToGenerate = historyO
                .map(history -> (List<Path>) ImmutableList.<Path>builder().addAll(resultDirs).add(history).build())
                .orElse(resultDirs);

            // Generate new report with history
            reportGenerator.generate(destination, resultDirsToGenerate, reportUrl);

            log.info("Report '{}' generated according to results '{}'", destination, resultDirsToGenerate);
        } finally {

            // Delete tmp history
            historyO.ifPresent(h -> deleteQuietly(h.toFile()));
            if (clearResults) {
                resultDirs.forEach(r -> deleteQuietly(r.toFile()));
            }
        }

        // New report entity
        final ReportEntity newEntity = ReportEntity.builder()
            .uuid(uuid)
            .path(reportPath)
            .createdDateTime(LocalDateTime.now(zeroZone()))
            .url(reportUrl)
            .level(prevEntity.map(e -> e.getLevel() + 1).orElse(0L))
            .active(true)
            .size(ReportEntity.sizeKB(destination))
            .buildUrl(
                // Get Build Url
                ofNullable(safeExecutorInfo.getBuildUrl())
                    // Or Build Name
                    .or(() -> ofNullable(safeExecutorInfo.getBuildName()))
                    // Or Executor Name
                    .or(() -> ofNullable(safeExecutorInfo.getName()))
                    // Or Executor Type
                    .orElse(safeExecutorInfo.getType())
            )
            .build();

        // Add request mapping
        redirection.mapRequestTo(newEntity.getPath(), reportsDir.resolve(uuid.toString()).toString());

        // Persist
        handleMaxHistory(newEntity);
        repository.saveAndFlush(newEntity);

        // Disable prev report
        prevEntity.ifPresent(e -> e.setActive(false));

        return newEntity;
    }

    ///// PRIVATE /////

    //region Private
    private void handleMaxHistory(ReportEntity created) {
        var max = cfg.reports().historyLevel();

        if (created.getLevel() >= max) { // Check reports count in history
            // Get all sorted reports
            var allReports = repository.findByPathOrderByCreatedDateTimeDesc(created.getPath());

            // If size more than max history
            if (allReports.size() >= max) {
                log.info("Current report count '{}' exceed max history report count '{}'",
                    allReports.size(),
                    max
                );

                // Delete last after max history
                long deleted = allReports.stream()
                    .skip(max)
                    .peek(e -> log.info("Report '{}' will be deleted", e))
                    .peek(e -> deleteQuietly(reportsDir.resolve(e.getUuid().toString()).toFile()))
                    .peek(repository::delete)
                    .count();

                // Update level (safety)
                created.setLevel(Math.max(created.getLevel() - deleted, 0));
            }
        }
    }

    @SneakyThrows
    private Optional<Path> copyHistory(Path reportPath, String prevReportWithHistoryUuid) {
        // History dir in report dir
        final Path sourceHistory = reportPath.resolve("history");

        // If History dir exists
        if (Files.exists(sourceHistory) && Files.isDirectory(sourceHistory)) {
            // Create tmp history dir
            final Path tmpHistory = reportsDir.resolve("history").resolve(prevReportWithHistoryUuid);
            FileUtils.moveDirectoryToDirectory(sourceHistory.toFile(), tmpHistory.toFile(), true);
            log.info("Report '{}' history is '{}'", reportPath, tmpHistory);
            return Optional.of(tmpHistory);
        } else {
            // Or nothing
            return Optional.empty();
        }
    }

    @NotNull
    private ExecutorInfo addExecutionInfo(Path resultPathWithInfo,
                                          ExecutorInfo executor,
                                          String reportUrl,
                                          UUID uuid) throws IOException {

        ExecutorInfo executorInfo = new ExecutorInfo();
        executorInfo.setReportUrl(reportUrl);
        executorInfo.setReportName(StringUtils.defaultIfBlank(executor.getReportName(), uuid.toString()));
        executorInfo.setName(StringUtils.defaultIfBlank(executor.getName(), "Remote executor"));
        executorInfo.setType(StringUtils.defaultIfBlank(executor.getType(), "CI"));
        executorInfo.setUrl(executor.getUrl());
        executorInfo.setBuildOrder(executor.getBuildOrder());
        executorInfo.setBuildName(executor.getBuildName());
        executorInfo.setBuildUrl(executor.getBuildUrl());

        final ObjectWriter writer = objectMapper.writer(new DefaultPrettyPrinter());
        final Path executorPath = resultPathWithInfo.resolve(JSON_FILE_NAME);
        writer.writeValue(executorPath.toFile(), executorInfo);
        log.info("Executor information added to '{}' : {}", executorPath, executorInfo);
        return executorInfo;
    }
    //endregion

    private String getExecutorReportUrl(ExecutorInfo executorInfo, String baseUrl, UUID uuid, String index_html) {
        return join(getReportHost(executorInfo, baseUrl), reportsDir, uuid.toString(), index_html);
    }

    private String getReportHost(ExecutorInfo executorInfo, String baseUrl) {
        return Optional.ofNullable(executorInfo)
            .map(ExecutorInfo::getReportUrl)
            .filter(StringUtils::isNotBlank)
            .orElse(baseUrl);
    }

}
