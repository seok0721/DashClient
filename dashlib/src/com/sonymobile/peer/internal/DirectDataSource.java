/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.sonymobile.peer.internal;

import java.io.EOFException;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import android.util.Log;

public class DirectDataSource extends DataSource {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "DirectDataSource";

    private static final int SIZE_INT = 4;

    private static final int SIZE_LONG = 8;

    private static final int SIZE_SHORT = 2;

    private FileDescriptor mFd;

    private FileInputStream mFis;

    private FileChannel mFileChannel;

    private RandomAccessFile mRandomAccessFile;

    private long mCurrentPosition;

    private long mStartOffset;

    private long mLength;

    public DirectDataSource(FileDescriptor fd, long offset, long length) {
        if (LOGS_ENABLED) Log.d(TAG, "Create DirectFDDataSource");

        try {
            setup(fd, offset, length);
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IO Exception", e);
            throw new IllegalArgumentException("Unsupported FileDescriptor");
        }
    }

    public DirectDataSource(String uri) {
        try {
            if (uri.startsWith("/") || uri.startsWith("file")) {
                mRandomAccessFile = new RandomAccessFile(new File(uri), "r");

                setup(mRandomAccessFile.getFD(), -1, -1);
            }

        } catch (FileNotFoundException e) {
            if (LOGS_ENABLED) Log.e(TAG, "File not found", e);
            throw new IllegalArgumentException("Unsupported uri");
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IO Exception", e);
            throw new IllegalArgumentException("Unsupported uri");
        }
    }

    private void setup(FileDescriptor fd, long offset, long length) throws IOException {
        mFd = fd;
        mFis = new FileInputStream(mFd);
        mFileChannel = mFis.getChannel();
        mStartOffset = offset >= 0 ? offset : 0;
        mLength = length > 0 ? length : Long.MAX_VALUE;
        mFileChannel = mFileChannel.position(mStartOffset);
        mCurrentPosition = mStartOffset;
    }

    @Override
    public void close() throws IOException {
        mFileChannel.close();
        mFis.close();

        if (mRandomAccessFile != null) {
            mRandomAccessFile.close();
        }
    }

    @Override
    public int readAt(long offset, byte[] buffer, int size) throws IOException {
        if (offset >= mLength) {
            return -1;
        }
        if (size > buffer.length) {
            throw new IllegalArgumentException("Size is larger than buffer");
        }

        int bytesToRead = size;
        if (offset + bytesToRead >= mLength) {
            bytesToRead = (int)(mLength - offset);
        }
        ByteBuffer bBuffer = ByteBuffer.wrap(buffer, 0, bytesToRead);
        int read = mFileChannel.read(bBuffer, offset + mStartOffset);
        mCurrentPosition = offset + read;
        return read;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        int bytesToRead = buffer.length;
        return readAt(mCurrentPosition, buffer, bytesToRead);
    }

    @Override
    public int readByte() throws IOException {
        if (mCurrentPosition >= mLength) {
            return -1;
        }
        byte[] data = new byte[1];
        int read = readAt(mCurrentPosition, data, 1);
        if (read > 0) {
            return data[0];
        }

        return -1;
    }

    @Override
    public short readShort() throws IOException, EOFException {
        if (mCurrentPosition + SIZE_SHORT >= mLength) {
            return -1;
        }
        byte[] shortBuffer = new byte[SIZE_SHORT];
        int read = readAt(mCurrentPosition, shortBuffer, shortBuffer.length);
        if (read <= 0) {
            // Since we know that is a error it should fit fine in a short
            return (short)read;
        }
        return peekShort(shortBuffer, 0);
    }

    @Override
    public int readInt() throws IOException, EOFException {
        if (mCurrentPosition + SIZE_INT >= mLength) {
            return -1;
        }
        byte[] intBuffer = new byte[SIZE_INT];
        int read = readAt(mCurrentPosition, intBuffer, intBuffer.length);
        if (read <= 0) {
            return read;
        }
        return peekInt(intBuffer, 0);
    }

    @Override
    public long readLong() throws IOException, EOFException {
        if (mCurrentPosition + SIZE_LONG >= mLength) {
            return -1;
        }
        byte[] longBuffer = new byte[SIZE_LONG];
        int read = readAt(mCurrentPosition, longBuffer, longBuffer.length);
        if (read <= 0) {
            return read;
        }
        return peekLong(longBuffer, 0);
    }

    @Override
    public long skipBytes(long count) throws IOException {
        mFileChannel = mFileChannel.position(mCurrentPosition + count);
        long skipped = mFileChannel.position() - mCurrentPosition;
        mCurrentPosition = mFileChannel.position();

        return skipped;
    }

    @Override
    public long length() throws IOException {
        return mFileChannel.size();
    }



    @Override
    public long getCurrentOffset() {
        return mCurrentPosition;
    }

    @Override
    public String getRemoteIP() {
        return null;
    }

    public void reset() {
        mCurrentPosition = mStartOffset;
    }

    public void seek(long offset) throws IOException {
        mFileChannel = mFileChannel.position(offset);
        mCurrentPosition = mFileChannel.position();
    }
}
