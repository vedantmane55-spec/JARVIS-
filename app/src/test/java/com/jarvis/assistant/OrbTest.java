package com.jarvis.assistant;

import android.view.View;
import java.nio.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class OrbTest {
    @Test public void onlyOrbVisibleUntilLongPressAndBackRestoresIt() {
        MainActivity ui=Robolectric.buildActivity(MainActivity.class).setup().get();
        assertEquals(View.VISIBLE,ui.orb.getVisibility());
        assertFalse(ui.inSettings());
        assertTrue(ui.orb.performLongClick());
        assertTrue(ui.inSettings());
        assertEquals(View.GONE,ui.orb.getVisibility());
        ui.onBackPressed();
        assertFalse(ui.inSettings());
        assertEquals(View.VISIBLE,ui.orb.getVisibility());
    }
    @Test public void legacyStudioPcmGetsPlayableWaveHeader() {
        byte[] wave=StudioVoice.wrapPcm(new byte[]{10,11,12,13},24000);
        ByteBuffer header=ByteBuffer.wrap(wave).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(48,wave.length);
        assertEquals(24000,header.getInt(24));
        assertEquals(4,header.getInt(40));
        assertEquals(13,wave[47]);
    }
}
