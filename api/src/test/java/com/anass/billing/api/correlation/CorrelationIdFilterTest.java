package com.anass.billing.api.correlation;

import com.anass.billing.common.correlation.CorrelationId;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private String run(MockHttpServletRequest req, MockHttpServletResponse res) throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(req, res, (r, s) -> seen.set(MDC.get(CorrelationId.MDC_KEY)));
        return seen.get();
    }

    @Test
    void generatesAnIdWhenHeaderIsMissing_putsItInMdc_andEchoesIt() throws Exception {
        var res = new MockHttpServletResponse();
        String seen = run(new MockHttpServletRequest(), res);

        assertThat(seen).isNotBlank();
        assertThat(res.getHeader(CorrelationId.HEADER)).isEqualTo(seen);
    }

    @Test
    void reusesAValidIncomingId() throws Exception {
        var req = new MockHttpServletRequest();
        req.addHeader(CorrelationId.HEADER, "req-123_abc");
        var res = new MockHttpServletResponse();

        assertThat(run(req, res)).isEqualTo("req-123_abc");
        assertThat(res.getHeader(CorrelationId.HEADER)).isEqualTo("req-123_abc");
    }

    @Test
    void replacesAnUnsafeIncomingId() throws Exception {
        var req = new MockHttpServletRequest();
        req.addHeader(CorrelationId.HEADER, "x\nFAKE LOG LINE");
        var res = new MockHttpServletResponse();

        String seen = run(req, res);

        assertThat(seen).isNotEqualTo("x\nFAKE LOG LINE");
        assertThat(CorrelationId.isSafe(seen)).isTrue();
    }

    @Test
    void clearsTheMdcAfterTheRequest() throws Exception {
        run(new MockHttpServletRequest(), new MockHttpServletResponse());
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }
}