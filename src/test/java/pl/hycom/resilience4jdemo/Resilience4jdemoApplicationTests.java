package pl.hycom.resilience4jdemo;

import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.fail;

import java.time.Duration;
import java.util.function.Function;

import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class Resilience4jdemoApplicationTests {

    interface RemoteService {
        int process(int i);
    }

    @Mock
    private RemoteService service; //Mock service

    @Test
    public void circuitBreakerTest() {
        //Circuit config
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(20)
                .ringBufferSizeInClosedState(5)
                .waitDurationInOpenState(Duration.ofMillis(1000))
                .build();

        //Circuit registration
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker circuitBreaker = registry.circuitBreaker("my");

        //Method decorating
        Function<Integer, Integer> decorated = CircuitBreaker.decorateFunction(circuitBreaker, service::process);

        //Mock behavior
        when(service.process(anyInt())).thenThrow(new RuntimeException());

        for (int i = 0; i < 12; i++) {
            try {
                decorated.apply(i);
            }
            catch (Exception ignore) {
            }
        }

        //Checking amount of service calls
        verify(service, times(5)).process(any(Integer.class));

        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        // Returns the failure rate in percentage.
        assertEquals(100f, metrics.getFailureRate());
        // Returns the current number of buffered calls.
        assertEquals(5, metrics.getNumberOfBufferedCalls());
        // Returns the current number of failed calls.
        assertEquals(5, metrics.getNumberOfFailedCalls());
    }

    @Test
    public void retryTest() {
        //Retry config
        RetryConfig config = RetryConfig.custom().maxAttempts(2).build();

        //Retry registration
        RetryRegistry registry = RetryRegistry.of(config);
        Retry retry = registry.retry("my");

        //Method decorating
        Function<Integer, Integer> decorated = Retry.decorateFunction(retry,service::process);

        //Mock behavior
        when(service.process(anyInt())).thenThrow(new RuntimeException());


        try {
            decorated.apply(1);
            fail("Expected an exception to be thrown if all retries failed");
        }
        catch (Exception e) {
            //Checking amount of service calls
            verify(service, times(2)).process(any(Integer.class));
        }
    }
}

