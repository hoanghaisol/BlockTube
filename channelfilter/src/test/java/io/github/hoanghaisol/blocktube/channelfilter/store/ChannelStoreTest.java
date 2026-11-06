package io.github.hoanghaisol.blocktube.channelfilter.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;

import io.github.hoanghaisol.blocktube.channelfilter.store.ChannelStore.Suggestion;

public class ChannelStoreTest {
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();

    @Test
    public void savesAndLoadsAutoBlockedWithReadFlag() throws Exception {
        File dir = mFolder.newFolder();
        ChannelStore store = new ChannelStore(dir);
        store.addSeen("UC1", "One");
        store.addAutoBlocked(Collections.singletonList(new Suggestion("UC2", "Two", 0.3f, "One")));
        store.save();

        ChannelStore loaded = new ChannelStore(dir);
        assertEquals("One", loaded.getName("UC1"));
        assertEquals(1, loaded.getUnreadAutoBlockedCount());

        loaded.markAutoBlockedRead();
        loaded.save();
        assertEquals(0, new ChannelStore(dir).getUnreadAutoBlockedCount());
    }

    /** filter.3 wrote version 3 (no read flag): the auto-blocked list must survive the update */
    @Test
    public void readsVersion3() throws Exception {
        File dir = mFolder.newFolder();
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(new File(dir, "store.bin")))) {
            out.writeInt(3);
            out.writeInt(0); // seen
            out.writeInt(0); // pending
            out.writeInt(0); // vectors
            out.writeInt(0); // dismissed
            out.writeInt(0); // suggestions
            out.writeInt(1); // auto-blocked
            out.writeUTF("UC2");
            out.writeBoolean(true);
            out.writeUTF("Two");
            out.writeFloat(0.3f);
            out.writeBoolean(true);
            out.writeUTF("One");
        }

        ChannelStore store = new ChannelStore(dir);
        assertTrue(store.getAutoBlockedIds().contains("UC2"));
        assertEquals(0, store.getUnreadAutoBlockedCount());
    }

    @Test
    public void unblockedElsewhereCountsAsDismissed() throws Exception {
        ChannelStore store = new ChannelStore(mFolder.newFolder());
        store.addAutoBlocked(Collections.singletonList(new Suggestion("UC2", "Two", 0.3f, "One")));

        store.reconcileAutoBlocked(Collections.emptySet());

        assertFalse(store.getAutoBlockedIds().contains("UC2"));
        assertTrue(store.getDismissed().contains("UC2"));
    }
}
