package com.maxrave.simpmusic.legacy;

import org.junit.Test;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.Description;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NewPipeApi18CompatibilityTest {
    @Test
    public void initializesYoutubeServiceWithoutJava9CollectionFactories() {
        assertEquals(5, ServiceList.all().size());
        assertNotNull(ServiceList.YouTube.getStreamLHFactory());
    }

    @Test
    public void streamDescriptionUsesApi18Fallback() {
        assertEquals("", new Description(null, Description.PLAIN_TEXT).getContent());
        assertEquals("description", new Description("description", Description.PLAIN_TEXT).getContent());
    }
}
