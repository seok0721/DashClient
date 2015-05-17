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

package com.sonymobile.peer.internal.mpegdash;

import com.sonymobile.peer.RepresentationSelector;
import com.sonymobile.peer.TrackInfo;
import com.sonymobile.peer.TrackRepresentation;
import com.sonymobile.peer.TrackInfo.TrackType;
import com.sonymobile.peer.internal.Configuration;
import com.sonymobile.peer.internal.mpegdash.MPDParser.AdaptationSet;
import com.sonymobile.peer.internal.mpegdash.MPDParser.Period;
import com.sonymobile.peer.internal.mpegdash.MPDParser.Representation;

import android.util.Log;

public class DefaultDASHRepresentationSelector implements RepresentationSelector {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "DefaultDASHRepresentationSelector";

    private MPDParser mMPDParser;

    private int mMaxBufferSize;

    public DefaultDASHRepresentationSelector(MPDParser parser, int maxBufferSize) {
        mMPDParser = parser;
        mMaxBufferSize = maxBufferSize;
    }

    @Override
    public void selectDefaultRepresentations(int[] selectedTracks, TrackInfo[] trackInfo,
            int[] selectedRepresentations) {
        // Select the audio representation with the highest quality
        if (selectedTracks[TrackType.AUDIO.ordinal()] >= 0) {
            long audioBitrate = 0;
            int audioRepresentation = 0;
            TrackRepresentation[] representations =
                    trackInfo[selectedTracks[TrackType.AUDIO.ordinal()]].getRepresentations();
            for (int i = 0; representations != null && i < representations.length; i++) {
                if (representations[i].getBitrate() > audioBitrate) {
                    audioBitrate = representations[i].getBitrate();
                    audioRepresentation = i;
                }
            }
            selectedRepresentations[TrackType.AUDIO.ordinal()] = audioRepresentation;
        } else {
            selectedRepresentations[TrackType.AUDIO.ordinal()] = -1;
        }

        // Select the first subtitle representation
        if (selectedTracks[TrackType.SUBTITLE.ordinal()] >= 0) {
            selectedRepresentations[TrackType.SUBTITLE.ordinal()] = 0;
        } else {
            selectedRepresentations[TrackType.SUBTITLE.ordinal()] = -1;
        }

        // Select the video representation with the lowest quality
        if (selectedTracks[TrackType.VIDEO.ordinal()] >= 0) {
            int worstBitrate = -1;
            int videoRepresentation = 0;
            TrackRepresentation[] representations =
                    trackInfo[selectedTracks[TrackType.VIDEO.ordinal()]].getRepresentations();
            for (int i = 0; representations != null && i < representations.length; i++) {
                if (worstBitrate == -1 || representations[i].getBitrate() < worstBitrate) {
                    worstBitrate = representations[i].getBitrate();
                    videoRepresentation = i;
                }
            }
            selectedRepresentations[TrackType.VIDEO.ordinal()] = videoRepresentation;
        } else {
            selectedRepresentations[TrackType.VIDEO.ordinal()] = -1;
        }
    }

    @Override
    public boolean selectRepresentations(long bandwidth, int[] selectedTracks,
            int[] selectedRepresentations) {
        boolean representationsChanged = false;
        Period period = mMPDParser.getActivePeriod();

        long audioBandwidth = 0;
        if (period.currentAdaptationSet[TrackType.AUDIO.ordinal()] > -1) {
            AdaptationSet audioAdaptationSet = period.adaptationSets
                    .get(period.currentAdaptationSet[TrackType.AUDIO.ordinal()]);

            int audioRepresentation = selectedRepresentations[TrackType.AUDIO.ordinal()];
            if (audioRepresentation == -1) {
                for (int i = 0; i < audioAdaptationSet.representations.size(); i++) {
                    Representation representation = audioAdaptationSet.representations.get(i);
                    if (representation.bandwidth > audioBandwidth) {
                        audioBandwidth = representation.bandwidth;
                        audioRepresentation = i;
                    }
                }
                selectedRepresentations[TrackType.AUDIO.ordinal()] = audioRepresentation;
                representationsChanged = true;
            } else {
                audioBandwidth = audioAdaptationSet.representations
                        .get(audioRepresentation).bandwidth;
            }
        }

        long subtitleBandwidth = 0;
        if (period.currentAdaptationSet[TrackType.SUBTITLE.ordinal()] > -1) {
            AdaptationSet subtitleAdaptationSet = period.adaptationSets
                    .get(period.currentAdaptationSet[TrackType.SUBTITLE.ordinal()]);

            int subtitleRepresentation = selectedRepresentations[TrackType.SUBTITLE.ordinal()];
            if (subtitleRepresentation == -1) {
                subtitleRepresentation = 0;
                selectedRepresentations[TrackType.SUBTITLE.ordinal()] = 0;
                representationsChanged = true;
            }

            subtitleBandwidth = subtitleAdaptationSet.representations
                    .get(subtitleRepresentation).bandwidth;
        }

        long availableBandwidth = 0;
        if (bandwidth > 0) {
            availableBandwidth = bandwidth - audioBandwidth - subtitleBandwidth;
        }

        if (mMaxBufferSize > 0) {
            long minBufferTimeSeconds = mMPDParser.getMinBufferTimeUs() / 1000000;
            long availableBuffer = mMaxBufferSize
                    - (int)((audioBandwidth + subtitleBandwidth) * minBufferTimeSeconds / 8);
            long availableBandwidthFromBuffer = availableBuffer * 8 / minBufferTimeSeconds;
            if (availableBandwidthFromBuffer < availableBandwidth) {
                if (LOGS_ENABLED)
                    Log.d(TAG, "available bandwidth capped to " + availableBandwidthFromBuffer
                            + " due to max buffer size");
                availableBandwidth = availableBandwidthFromBuffer;
            }
        }

        if (period.currentAdaptationSet[TrackType.VIDEO.ordinal()] > -1) {
            AdaptationSet videoAdaptationSet = period.adaptationSets
                    .get(period.currentAdaptationSet[TrackType.VIDEO.ordinal()]);

            int currentVideoRepresentation = selectedRepresentations[TrackType.VIDEO.ordinal()];
            int currentSelectedBandwidth = 0;
            if (currentVideoRepresentation > -1) {
                currentSelectedBandwidth = videoAdaptationSet.representations
                        .get(currentVideoRepresentation).bandwidth;
            }

            int videoRepresentation = -1;
            int bestBandwidth = 0;
            for (int i = videoAdaptationSet.representations.size() - 1; i >= 0; i--) {
                Representation representation = videoAdaptationSet.representations.get(i);

                if (representation.selected && availableBandwidth > representation.bandwidth) {
                    if (representation.bandwidth > currentSelectedBandwidth
                            && availableBandwidth < (float)representation.bandwidth * 1.3) {
                        continue;
                    }

                    if (representation.bandwidth > bestBandwidth) {
                        videoRepresentation = i;
                        bestBandwidth = representation.bandwidth;
                    }
                }
            }

            if (videoRepresentation == -1) {
                int worstBandwidth = -1;
                for (int i = 0; i < videoAdaptationSet.representations.size(); i++) {
                    Representation representation = videoAdaptationSet.representations.get(i);

                    if (representation.selected
                            && (worstBandwidth == -1
                            || representation.bandwidth < worstBandwidth)) {
                        worstBandwidth = representation.bandwidth;
                        videoRepresentation = i;
                    }
                }
            }

            if (videoRepresentation != currentVideoRepresentation) {
                selectedRepresentations[TrackType.VIDEO.ordinal()] = videoRepresentation;
                representationsChanged = true;
            }
        }

        return representationsChanged;
    }
}
