package qa.allure.helper.plugin;

import io.qameta.allure.core.LaunchResults;
import org.springframework.beans.factory.BeanFactory;
import qa.allure.properties.AllureProperties;
import qa.allure.properties.TmsProperties;

import java.nio.file.Path;
import java.util.Collection;

public interface AllureServerPlugin {

    void onGenerationStart(Collection<Path> resultsDirectories, Context context);

    void onGenerationFinish(Path reportDirectory, Collection<LaunchResults> launchResults, Context context);

    String getName();

    default boolean isEnabled(Context context) {
        return true;
    }

    interface Context {

        AllureProperties getAllureProperties();

        TmsProperties tmsProperties();

        BeanFactory beanFactory();

        String getReportUrl();
    }
}
