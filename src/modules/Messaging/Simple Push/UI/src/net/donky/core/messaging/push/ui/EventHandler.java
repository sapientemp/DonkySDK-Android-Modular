package net.donky.core.messaging.push.ui;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;

import net.donky.core.messaging.push.logic.SimplePushData;
import net.donky.core.messaging.push.logic.events.SimplePushMessageEvent;
import net.donky.core.network.assets.DonkyAssetController;
import net.donky.core.network.assets.NotificationImageLoader;

import java.io.IOException;

/**
 * Class to handle local events with received simple push data.
 *
 * Created by Marcin Swierczek
 * 10/04/2015.
 * Copyright (C) Donky Networks Ltd. All rights reserved.
 */
public class EventHandler {

    /**
     * Handle local events with received simple push data. Displays the notification in notification centre.
     *
     * @param context Application context.
     * @param event Local event with received simple push data.
     * @param simplePushUIConfiguration UI configuration for system notification.
     */
    public void handleSimplePushEvent(Context context, SimplePushMessageEvent event, SimplePushUIConfiguration simplePushUIConfiguration) {

        if (event != null && !event.isReceivedExpired()) {

            displayNotification(context, simplePushUIConfiguration, event.getSimplePushData());

        }

    }

    /**
     * Downloads avatar and displays simple push notification.
     *
     * @param context Application context.
     * @param simplePushUIConfiguration UI configuration for system notification.
     * @param simplePushData Description of the simple push message.
     */
    public void displayNotification(final Context context, final SimplePushUIConfiguration simplePushUIConfiguration, final SimplePushData simplePushData) {

        if (simplePushData != null && simplePushData.getAvatarAssetId() != null) {

            DonkyAssetController.getInstance().downloadAvatar(simplePushData.getAvatarAssetId(), new NotificationImageLoader(context) {

                @Override
                public void success(Bitmap bitmap) {

                    displayNotification(context, simplePushData, simplePushUIConfiguration, bitmap);

                }

                @Override
                public void failure(IOException e) {

                    displayNotification(context, simplePushData, simplePushUIConfiguration, null);

                }

            });

        } else {

            displayNotification(context, simplePushData, simplePushUIConfiguration, null);

        }

    }

    /**
     * Displays simple push notification.
     *
     * @param context Application context.
     * @param data Description of the simple push message.
     * @param simplePushUIConfiguration UI configuration for system notification.
     * @param avatar Avatar image.
     */
    private void displayNotification(Context context, SimplePushData data, SimplePushUIConfiguration simplePushUIConfiguration, Bitmap avatar) {

        int id = data.getMessageId().hashCode();

        Notification notification = new SystemNotificationBuilder(id).buildSimplePushUINotification(context, simplePushUIConfiguration, data, avatar);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(id, notification);

    }
}