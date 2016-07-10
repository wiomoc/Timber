package com.naman14.timber.remote;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import fi.iki.elonen.NanoHTTPD;


/**
 * Created by Christoph on 09.07.2016.
 */

public class PCMUtils {

    final static String LOG_TAG = "PCM";
    private int channels;
    private int sampleRate;
    private MediaFormat format;
    private MediaExtractor extractor;
    String file;

    private static byte[] toBytes(int i) {
        byte[] result = new byte[4];

        result[3] = (byte) (i >> 24);
        result[2] = (byte) (i >> 16);
        result[1] = (byte) (i >> 8);
        result[0] = (byte) (i /*>> 0*/);

        return result;
    }

    private static byte[] toBytesShort(int i) {
        byte[] result = new byte[2];

        result[1] = (byte) (i >> 8);
        result[0] = (byte) (i /*>> 0*/);

        return result;
    }


    PCMUtils(String file) {
        extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file);
            format = extractor.getTrackFormat(0);
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        } catch (IOException e) {

        }

    }

    void decode(OutputStream waveout) throws IOException {

        MediaCodec codec;
        int inputBufIndex;
        ByteBuffer[] codecInputBuffers;
        ByteBuffer[] codecOutputBuffers;

        String mime = format.getString(MediaFormat.KEY_MIME);

        // the actual decoder

        codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
        codec.start();
        codecInputBuffers = codec.getInputBuffers();
        codecOutputBuffers = codec.getOutputBuffers();
        /*
        long duration = format.getLong(MediaFormat.KEY_DURATION);
        int byteDepth = 2;
        int samplesize = byteDepth * channels;
        int byteRate = samplesize * sampleRate;
        int lenght = (int) ((duration / 1000) * (byteRate / 1000.0));
        waveout.write("RIFF".getBytes());
        waveout.write(toBytes(lenght + 32));
        waveout.write("WAVEfmt ".getBytes());
        waveout.write(toBytes(16));
        waveout.write(toBytesShort(1));
        waveout.write(toBytesShort(channels));
        waveout.write(toBytes(sampleRate));
        waveout.write(toBytes(channels * sampleRate * 2));
        waveout.write(toBytesShort(channels * 2));
        waveout.write(toBytesShort(16));

        waveout.write("data".getBytes());
        waveout.write(toBytes(lenght));
        */
        extractor.selectTrack(0);

        // start decoding
        final long kTimeOutUs = 10000;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        int noOutputCounter = 0;
        int noOutputCounterLimit = 50;
        while (!sawOutputEOS && noOutputCounter < noOutputCounterLimit) {
            noOutputCounter++;
            if (!sawInputEOS) {
                inputBufIndex = codec.dequeueInputBuffer(kTimeOutUs);
                Log.d(LOG_TAG, " bufIndexCheck " + inputBufIndex);
                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];

                    int sampleSize =
                            extractor.readSampleData(dstBuf, 0 /* offset */);

                    long presentationTimeUs = 0;

                    if (sampleSize < 0) {
                        Log.d(LOG_TAG, "saw input EOS.");
                        sawInputEOS = true;
                        sampleSize = 0;
                    } else {
                        presentationTimeUs = extractor.getSampleTime();
                    }
                    codec.queueInputBuffer(inputBufIndex, 0 /* offset */, sampleSize, presentationTimeUs, sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    if (!sawInputEOS) {
                        extractor.advance();
                    }
                } else {
                    Log.e(LOG_TAG, "inputBufIndex " + inputBufIndex);
                }
            }
            int res = codec.dequeueOutputBuffer(info, kTimeOutUs);
            if (res >= 0) {

                Log.d(LOG_TAG, "got frame, size " + info.size + "/" + info.presentationTimeUs);
                if (info.size > 0) {
                    noOutputCounter = 0;
                }

                ByteBuffer buf = codecOutputBuffers[res];
                final byte[] chunk = new byte[info.size];
                // buf.get(chunk);
                //Convert Endians
                for (int i = 0; i < info.size; i += 2) {
                    chunk[i + 1] = buf.get(i);
                    chunk[i] = buf.get(i + 1);
                }
                buf.clear();
                if (chunk.length > 0) {
                    try {
                        waveout.write(chunk, 0, chunk.length);
                    } catch (SocketException e) {
                        codec.stop();
                        codec.release();
                        extractor.release();
                        return;
                    }

                }
                codec.releaseOutputBuffer(res, false /* render */);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
            } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();
            }
        }

        codec.stop();
        codec.release();
        extractor.release();
    }

    public static class TrancodingResponse extends NanoHTTPD.Response {
        private String file;
        String waveinfo;

        public TrancodingResponse(String file, String waveinfo) {
            super(null, null, null, 0);
            Log.d("new", "TrancodingResponse " + waveinfo);
            this.waveinfo = waveinfo;
            this.file = file;
        }

        @Override
        protected void send(OutputStream outputStream) {
            SimpleDateFormat gmtFrmt = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
            gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));

            try {
                PCMUtils pcm = new PCMUtils(this.file);
                PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream)));
                pw.append("HTTP/1.1 ").append(Status.OK.getDescription()).append(" \r\n");
                printHeader(pw, "Content-Type", "audio/L16;rate="+pcm.sampleRate+";channels="+pcm.channels);
                printHeader(pw, "Date", gmtFrmt.format(new Date()));
                printHeader(pw, "ContentFeatures.DLNA.ORG", waveinfo);
                printHeader(pw, "Scid.DLNA.ORG", "134252567");
                printHeader(pw, "TransferMode.DLNA.ORG", "Streaming");
                printHeader(pw, "Connection", "close");
                pw.append("\r\n");
                pw.flush();
                pcm.decode(outputStream);
                outputStream.flush();

            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
}
