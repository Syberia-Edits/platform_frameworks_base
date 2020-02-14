/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.people.data;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManagerInternal;
import android.content.ComponentName;
import android.content.LocusId;
import android.text.format.DateUtils;
import android.util.ArrayMap;

import com.android.server.LocalServices;

import java.util.Map;
import java.util.function.Function;

/** A helper class that queries {@link UsageStatsManagerInternal}. */
class UsageStatsQueryHelper {

    private final UsageStatsManagerInternal mUsageStatsManagerInternal;
    private final int mUserId;
    private final Function<String, PackageData> mPackageDataGetter;
    // Activity name -> Conversation start event (LOCUS_ID_SET)
    private final Map<ComponentName, UsageEvents.Event> mConvoStartEvents = new ArrayMap<>();
    private long mLastEventTimestamp;

    /**
     * @param userId The user whose events are to be queried.
     * @param packageDataGetter The function to get {@link PackageData} with a package name.
     */
    UsageStatsQueryHelper(@UserIdInt int userId,
            Function<String, PackageData> packageDataGetter) {
        mUsageStatsManagerInternal = LocalServices.getService(UsageStatsManagerInternal.class);
        mUserId = userId;
        mPackageDataGetter = packageDataGetter;
    }

    /**
     * Queries {@link UsageStatsManagerInternal} for the recent events occurred since {@code
     * sinceTime} and adds the derived {@link Event}s into the corresponding package's event store,
     *
     * @return true if the query runs successfully and at least one event is found.
     */
    boolean querySince(long sinceTime) {
        UsageEvents usageEvents = mUsageStatsManagerInternal.queryEventsForUser(
                mUserId, sinceTime, System.currentTimeMillis(), UsageEvents.SHOW_ALL_EVENT_DATA);
        if (usageEvents == null) {
            return false;
        }
        boolean hasEvents = false;
        while (usageEvents.hasNextEvent()) {
            UsageEvents.Event e = new UsageEvents.Event();
            usageEvents.getNextEvent(e);

            hasEvents = true;
            mLastEventTimestamp = Math.max(mLastEventTimestamp, e.getTimeStamp());
            String packageName = e.getPackageName();
            PackageData packageData = mPackageDataGetter.apply(packageName);
            if (packageData == null) {
                continue;
            }
            switch (e.getEventType()) {
                case UsageEvents.Event.SHORTCUT_INVOCATION:
                    addEventByShortcutId(packageData, e.getShortcutId(),
                            new Event(e.getTimeStamp(), Event.TYPE_SHORTCUT_INVOCATION));
                    break;
                case UsageEvents.Event.NOTIFICATION_INTERRUPTION:
                    addEventByNotificationChannelId(packageData, e.getNotificationChannelId(),
                            new Event(e.getTimeStamp(), Event.TYPE_NOTIFICATION_POSTED));
                    break;
                case UsageEvents.Event.LOCUS_ID_SET:
                    onInAppConversationEnded(packageData, e);
                    LocusId locusId = e.getLocusId() != null ? new LocusId(e.getLocusId()) : null;
                    if (locusId != null) {
                        if (packageData.getConversationStore().getConversationByLocusId(locusId)
                                != null) {
                            ComponentName activityName =
                                    new ComponentName(packageName, e.getClassName());
                            mConvoStartEvents.put(activityName, e);
                        }
                    }
                    break;
                case UsageEvents.Event.ACTIVITY_PAUSED:
                case UsageEvents.Event.ACTIVITY_STOPPED:
                case UsageEvents.Event.ACTIVITY_DESTROYED:
                    onInAppConversationEnded(packageData, e);
                    break;
            }
        }
        return hasEvents;
    }

    long getLastEventTimestamp() {
        return mLastEventTimestamp;
    }

    private void onInAppConversationEnded(@NonNull PackageData packageData,
            @NonNull UsageEvents.Event endEvent) {
        ComponentName activityName =
                new ComponentName(endEvent.getPackageName(), endEvent.getClassName());
        UsageEvents.Event startEvent = mConvoStartEvents.remove(activityName);
        if (startEvent == null || startEvent.getTimeStamp() >= endEvent.getTimeStamp()) {
            return;
        }
        long durationMillis = endEvent.getTimeStamp() - startEvent.getTimeStamp();
        Event event = new Event.Builder(startEvent.getTimeStamp(), Event.TYPE_IN_APP_CONVERSATION)
                .setDurationSeconds((int) (durationMillis / DateUtils.SECOND_IN_MILLIS))
                .build();
        addEventByLocusId(packageData, new LocusId(startEvent.getLocusId()), event);
    }

    private void addEventByShortcutId(PackageData packageData, String shortcutId, Event event) {
        if (packageData.getConversationStore().getConversation(shortcutId) == null) {
            return;
        }
        EventHistoryImpl eventHistory = packageData.getEventStore().getOrCreateEventHistory(
                EventStore.CATEGORY_SHORTCUT_BASED, shortcutId);
        eventHistory.addEvent(event);
    }

    private void addEventByLocusId(PackageData packageData, LocusId locusId, Event event) {
        if (packageData.getConversationStore().getConversationByLocusId(locusId) == null) {
            return;
        }
        EventHistoryImpl eventHistory = packageData.getEventStore().getOrCreateEventHistory(
                EventStore.CATEGORY_LOCUS_ID_BASED, locusId.getId());
        eventHistory.addEvent(event);
    }

    private void addEventByNotificationChannelId(PackageData packageData,
            String notificationChannelId, Event event) {
        ConversationInfo conversationInfo =
                packageData.getConversationStore().getConversationByNotificationChannelId(
                        notificationChannelId);
        if (conversationInfo == null) {
            return;
        }
        EventHistoryImpl eventHistory = packageData.getEventStore().getOrCreateEventHistory(
                EventStore.CATEGORY_SHORTCUT_BASED, conversationInfo.getShortcutId());
        eventHistory.addEvent(event);
    }
}
