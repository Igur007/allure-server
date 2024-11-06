package qa.allure.gui;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import lombok.extern.slf4j.Slf4j;
import qa.allure.properties.AllureProperties;

@Slf4j
public class DateTimeResolverTest {

    private DateTimeResolver resolverSpy;

    @BeforeEach
    public void setUp() {

        var resolver = new DateTimeResolver(
            new AllureProperties(null, null, "yy/MM/dd HH:mm:ss", null, null, null)
        );
        var formatterInCurrentTimeZone = DateTimeFormatter.ofPattern("yy/MM/dd HH:mm:ss").withZone(TimeZone.getDefault().toZoneId());

        resolverSpy = Mockito.spy(resolver);
        Mockito.when(resolverSpy.acquireFormatter()).thenReturn(formatterInCurrentTimeZone);
    }

    @Test
    public void printDate() {
        var dateInZeroTimeZone = LocalDateTime.now(ZoneOffset.UTC);

        String res = resolverSpy.printDate(dateInZeroTimeZone);
        log.info(res);

    }
}
