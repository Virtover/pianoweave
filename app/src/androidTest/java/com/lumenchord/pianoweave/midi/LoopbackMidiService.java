package com.lumenchord.pianoweave.midi;

import android.media.midi.MidiDeviceService;
import android.media.midi.MidiReceiver;

import java.io.IOException;

/**
 * Test-only virtual keyboard: bytes sent to its input port come straight out of its output port.
 * Written in Java because it runs in the test APK's own process, which has no Kotlin runtime.
 */
public class LoopbackMidiService extends MidiDeviceService {
    @Override
    public MidiReceiver[] onGetInputPortReceivers() {
        return new MidiReceiver[] {
            new MidiReceiver() {
                @Override
                public void onSend(byte[] msg, int offset, int count, long timestamp) throws IOException {
                    MidiReceiver[] outputs = getOutputPortReceivers();
                    if (outputs.length > 0) outputs[0].send(msg, offset, count, timestamp);
                }
            }
        };
    }
}
