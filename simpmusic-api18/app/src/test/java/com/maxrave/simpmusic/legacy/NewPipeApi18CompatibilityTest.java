package com.maxrave.simpmusic.legacy;

import org.junit.Test;
import org.schabi.newpipe.extractor.ServiceList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NewPipeApi18CompatibilityTest {
    @Test
    public void initializesYoutubeServiceWithoutJava9CollectionFactories() {
        assertEquals(5, ServiceList.all().size());
        assertNotNull(ServiceList.YouTube.getStreamLHFactory());
    }
}
