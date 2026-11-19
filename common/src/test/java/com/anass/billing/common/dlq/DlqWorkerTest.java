package com.anass.billing.common.dlq;

import com.anass.billing.common.messaging.DlqWorker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DlqWorkerTest {
    @Test
    void fromPathName_findsEachWorker() {
        assertThat(DlqWorker.fromPathName("invoice")).contains(DlqWorker.INVOICE);
        assertThat(DlqWorker.fromPathName("email")).contains(DlqWorker.EMAIL);
        assertThat(DlqWorker.fromPathName("audit")).contains(DlqWorker.AUDIT);
    }

    @Test
    void fromPathName_unknownName_isEmpty() {
        assertThat(DlqWorker.fromPathName("shipping")).isEmpty();
    }
}
