package io.dropwizard.metrics.jetty12.ee10;

import com.codahale.metrics.MetricRegistry;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextRequest;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.junit.Ignore;
import org.junit.Test;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.annotation.ResponseMeteredLevel.ALL;
import static com.codahale.metrics.annotation.ResponseMeteredLevel.COARSE;
import static com.codahale.metrics.annotation.ResponseMeteredLevel.DETAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class InstrumentedEE10HandlerTest extends AbstractIntegrationTest {

    @Override
    protected Handler getHandler() {
        InstrumentedEE10HandlerTest.TestHandler testHandler = new InstrumentedEE10HandlerTest.TestHandler();
        // a servlet handler needs a servlet mapping, else the request will be short-circuited
        // so use the DefaultServlet here
        testHandler.addServletWithMapping(DefaultServlet.class, "/");
        return testHandler;
    }

    @Test
    public void hasAName() throws Exception {
        assertThat(handler.getName())
                .isEqualTo("handler");
    }

    @Test
    public void createsAndRemovesMetricsForTheHandler() throws Exception {
        final ContentResponse response = client.GET(uri("/hello"));

        assertThat(response.getStatus())
                .isEqualTo(404);

        assertThat(registry.getNames())
                .containsOnly(
                        MetricRegistry.name(TestHandler.class, "handler.1xx-responses"),
                        MetricRegistry.name(TestHandler.class, "handler.2xx-responses"),
                        MetricRegistry.name(TestHandler.class, "handler.3xx-responses"),
                        MetricRegistry.name(TestHandler.class, "handler.4xx-responses"),
                        MetricRegistry.name(TestHandler.class, "handler.404-responses"),
                        MetricRegistry.name(TestHandler.class, "handler.5xx-responses"),
                        MetricRegistry.name(TestHandler.class, "handler.percent-4xx-1m"),
                        MetricRegistry.name(TestHandler.class, "handler.percent-4xx-5m"),
                        MetricRegistry.name(TestHandler.class, "handler.percent-4xx-15m"),
                        MetricRegistry.name(TestHandler.class, "handler.percent-5xx-1m"),
                        MetricRegistry.name(TestHandler.class, "handler.percent-5xx-5m"),
                        MetricRegistry.name(TestHandler.class, "handler.percent-5xx-15m"),
                        MetricRegistry.name(TestHandler.class, "handler.requests"),
                        MetricRegistry.name(TestHandler.class, "handler.active-suspended"),
                        MetricRegistry.name(TestHandler.class, "handler.async-dispatches"),
                        MetricRegistry.name(TestHandler.class, "handler.async-timeouts"),
                        MetricRegistry.name(TestHandler.class, "handler.get-requests"),
                        MetricRegistry.name(TestHandler.class, "handler.put-requests"),
                        MetricRegistry.name(TestHandler.class, "handler.active-dispatches"),
                        MetricRegistry.name(TestHandler.class, "handler.trace-requests"),
                        MetricRegistry.name(TestHandler.class, "handler.other-requests"),
                        MetricRegistry.name(TestHandler.class, "handler.connect-requests"),
                        MetricRegistry.name(TestHandler.class, "handler.dispatches"),
                        MetricRegistry.name(TestHandler.class, "handler.head-requests"),
                        MetricRegistry.name(TestHandler.class, "handler.post-requests"),
                        MetricRegistry.name(TestHandler.class, "handler.options-requests"),
                        MetricRegistry.name(TestHandler.class, "handler.active-requests"),
                        MetricRegistry.name(TestHandler.class, "handler.delete-requests"),
                        MetricRegistry.name(TestHandler.class, "handler.move-requests")
                );

        server.stop();

        assertThat(registry.getNames())
                .isEmpty();
    }

    @Test
    @Ignore("flaky on virtual machines")
    public void responseTimesAreRecordedForBlockingResponses() throws Exception {

        final ContentResponse response = client.GET(uri("/blocking"));

        assertThat(response.getStatus())
                .isEqualTo(200);

        assertResponseTimesValid();
    }

    @Test
    public void doStopDoesNotThrowNPE() throws Exception {
        InstrumentedEE10Handler handler = new InstrumentedEE10Handler(registry, null, ALL);
        handler.setHandler(new TestHandler());

        assertThatCode(handler::doStop).doesNotThrowAnyException();
    }

    @Test
    public void gaugesAreRegisteredWithResponseMeteredLevelCoarse() throws Exception {
        InstrumentedEE10Handler handler = new InstrumentedEE10Handler(registry, "coarse", COARSE);
        handler.setHandler(new TestHandler());
        handler.setName("handler");
        handler.doStart();
        assertThat(registry.getGauges()).containsKey("coarse.handler.percent-4xx-1m");
    }

    @Test
    public void gaugesAreNotRegisteredWithResponseMeteredLevelDetailed() throws Exception {
        InstrumentedEE10Handler handler = new InstrumentedEE10Handler(registry, "detailed", DETAILED);
        handler.setHandler(new TestHandler());
        handler.setName("handler");
        handler.doStart();
        assertThat(registry.getGauges()).doesNotContainKey("coarse.handler.percent-4xx-1m");
    }

    @Test
    @Ignore("flaky on virtual machines")
    public void responseTimesAreRecordedForAsyncResponses() throws Exception {

        final ContentResponse response = client.GET(uri("/async"));

        assertThat(response.getStatus())
                .isEqualTo(200);

        assertResponseTimesValid();
    }

    private void assertResponseTimesValid() {
        assertThat(registry.getMeters().get(metricName() + ".2xx-responses")
                .getCount()).isGreaterThan(0L);
        assertThat(registry.getMeters().get(metricName() + ".200-responses")
                .getCount()).isGreaterThan(0L);


        assertThat(registry.getTimers().get(metricName() + ".get-requests")
                .getSnapshot().getMedian()).isGreaterThan(0.0).isLessThan(TimeUnit.SECONDS.toNanos(1));

        assertThat(registry.getTimers().get(metricName() + ".requests")
                .getSnapshot().getMedian()).isGreaterThan(0.0).isLessThan(TimeUnit.SECONDS.toNanos(1));
    }

    private String metricName() {
        return MetricRegistry.name(TestHandler.class.getName(), "handler");
    }

    /**
     * test handler.
     * <p>
     * Supports
     * <p>
     * /blocking - uses the standard servlet api
     * /async - uses the 3.1 async api to complete the request
     * <p>
     * all other requests will return 404
     */
    private static class TestHandler extends ServletHandler {
        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            ServletContextRequest servletContextRequest = Request.as(request, ServletContextRequest.class);
            if (servletContextRequest == null) {
                return false;
            }

            HttpServletRequest httpServletRequest = servletContextRequest.getServletApiRequest();
            HttpServletResponse httpServletResponse = servletContextRequest.getHttpServletResponse();

            String path = request.getHttpURI().getPath();
            switch (path) {
                case "/blocking":
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    httpServletResponse.setStatus(200);
                    httpServletResponse.setContentType("text/plain");
                    httpServletResponse.getWriter().write("some content from the blocking request\n");
                    callback.succeeded();
                    return true;
                case "/async":
                    servletContextRequest.getState().handling();
                    final AsyncContext context = httpServletRequest.startAsync();
                    Thread t = new Thread(() -> {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        httpServletResponse.setStatus(200);
                        httpServletResponse.setContentType("text/plain");
                        final ServletOutputStream servletOutputStream;
                        try {
                            servletOutputStream = httpServletResponse.getOutputStream();
                            servletOutputStream.setWriteListener(
                                    new WriteListener() {
                                        @Override
                                        public void onWritePossible() throws IOException {
                                            servletOutputStream.write("some content from the async\n"
                                                    .getBytes(StandardCharsets.UTF_8));
                                            context.complete();
                                            servletContextRequest.getServletChannel().handle();
                                        }

                                        @Override
                                        public void onError(Throwable throwable) {
                                            context.complete();
                                            servletContextRequest.getServletChannel().handle();
                                        }
                                    }
                            );
                            servletContextRequest.getHttpOutput().run();
                        } catch (IOException e) {
                            context.complete();
                            servletContextRequest.getServletChannel().handle();
                        }
                    });
                    t.start();
                    return true;
                default:
                    return false;
            }
        }
    }
}
