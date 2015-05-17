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

package com.sonymobile.seeder.internal;

import java.util.Hashtable;

import com.sonymobile.seeder.MetaData;

public class MetaDataImpl implements MetaData {

    Hashtable<String, Object> mMetaDataValues;

    public MetaDataImpl() {
        mMetaDataValues = new Hashtable<String, Object>();
    }

    @Override
    public int getInteger(String key) {
        if (mMetaDataValues.containsKey(key)) {
            Integer val = (Integer)mMetaDataValues.get(key);
            return val.intValue();
        }
        return Integer.MIN_VALUE;
    }

    @Override
    public long getLong(String key) {
        if (mMetaDataValues.containsKey(key)) {
            Long val = (Long)(mMetaDataValues.get(key));
            return val.longValue();
        }
        return Long.MIN_VALUE;
    }

    @Override
    public float getFloat(String key) {
        if (mMetaDataValues.containsKey(key)) {
            Float val = (Float)(mMetaDataValues.get(key));
            return val.floatValue();
        }
        return Float.MIN_VALUE;
    }

    @Override
    public String getString(String key) {
        Object value = mMetaDataValues.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    @Override
    public String getString(String key1, String key2) {
        // not applicable for this class
        return null;
    }

    @Override
    public byte[] getByteBuffer(String key) {
        return (byte[])(mMetaDataValues.get(key));
    }

    @Override
    public byte[] getByteBuffer(String key1, String key2) {
        // not applicable for this class
        return null;
    }

    @Override
    public String[] getStringArray(String key) {
        Object[] values = (Object[])mMetaDataValues.get(key);
        String[] strings = new String[values.length];
        System.arraycopy(values, 0, strings, 0, values.length);
        return strings;
    }

    @Override
    public boolean containsKey(String key) {
        return mMetaDataValues.containsKey(key);
    }

    public void addValue(String key, Object value) {
        // Hashtable doesn't allow null-values.
        if (value != null) {
            mMetaDataValues.put(key, value);
        }
    }

}
