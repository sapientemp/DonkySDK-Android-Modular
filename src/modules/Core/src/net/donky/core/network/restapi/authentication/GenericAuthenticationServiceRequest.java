package net.donky.core.network.restapi.authentication;

import net.donky.core.DonkyException;
import net.donky.core.DonkyListener;
import net.donky.core.DonkyResultListener;
import net.donky.core.account.DonkyAccountController;
import net.donky.core.logging.DLog;
import net.donky.core.model.DonkyDataController;
import net.donky.core.network.NetworkResultListener;
import net.donky.core.network.OnConnectionListener;
import net.donky.core.network.RetryPolicy;
import net.donky.core.network.UserSuspendedException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

import retrofit.RetrofitError;
import retrofit.mime.TypedInput;

/**
 * Implements basic responsibilities of every authentication network request.
 *
 * Created by Marcin Swierczek
 * 09/03/2015
 * Copyright (C) Donky Networks Ltd. All rights reserved.
 */
public abstract class GenericAuthenticationServiceRequest<T> extends OnConnectionListener {

    private final RetryPolicy retryPolicy;

    protected GenericAuthenticationServiceRequest() {
        this.retryPolicy = new RetryPolicy();
    }

    /**
     * @return Retry policy used for this network request.
     */
    RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    /**
     * Register request to listen for connection restored system events.
     */
    protected abstract void doStartListenForConnectionRestored();

    /**
     * Synchronous implementation of particular REST call.
     *
     * @param apiKey Application space unique identifier on Donky Network.
     * @return Result of the network call.
     */
    protected abstract T doSynchronousCall(String apiKey) throws RetrofitError;

    /**
     * Asynchronous implementation of particular REST call.
     *
     * @param apiKey   Application space unique identifier on Donky Network.
     * @param listener The callback to invoke when the command has executed.
     */
    protected abstract void doAsynchronousCall(String apiKey, NetworkResultListener<T> listener);

    /**
     * Perform synchronous network call.
     * This will handle automatically:
     * - Internet connection changes.
     * - Network errors and retries.
     *
     * @return Generic result of network call.
     * @throws DonkyException
     */
    public T performSynchronous(final String apiKey) throws DonkyException {

        if (isConnectionAvailable()) {

            try {

                return doSynchronousCall(apiKey);

            } catch (RetrofitError error) {

                retrofit.client.Response r = error.getResponse();

                if (r != null) {

                    int statusCode = r.getStatus();

                    if (statusCode == 400) {

                        TypedInput body = r.getBody();

                        new DLog("GenericAuthenticationServiceRequest").error("Client Bad Request " + readInputStream(body), error);

                    }

                    if (getRetryPolicy().shouldRetryForStatusCode(statusCode) && getRetryPolicy().retry()) {

                        try {
                            Thread.sleep(getRetryPolicy().getDelayBeforeNextRetry());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        return performSynchronous(apiKey);

                    } else if (statusCode == 401) {

                        DonkyAccountController.getInstance().reRegisterWithSameUserDetails(null);

                        DonkyException donkyException = new DonkyException("Error performing network call. User don't exist. Re-registering if user was registered.");
                        donkyException.initCause(error);
                        throw donkyException;

                    } else if (statusCode == 403) {

                        DonkyAccountController.getInstance().setSuspended(true);

                        UserSuspendedException userSuspendedException = new UserSuspendedException();
                        userSuspendedException.initCause(error);
                        throw userSuspendedException;

                    } else {

                        DonkyException donkyException = new DonkyException("Error performing network call.");
                        donkyException.initCause(error);
                        throw donkyException;

                    }

                } else {

                    DonkyException donkyException = new DonkyException("Error performing network call. Null response.");
                    donkyException.initCause(error);
                    throw donkyException;

                }
            }
        } else {

            doStartListenForConnectionRestored();
            throw new DonkyException("Internet connection not available.");
        }
    }

    /**
     * Perform asynchronous network call.
     * This will handle automatically:
     * - Internet connection changes.
     * - Network errors and retries.
     *
     * @param apiKey   Application space unique identifier on Donky Network.
     * @param listener The callback to invoke when the command has executed.
     */
    public void performAsynchronous(final String apiKey, final DonkyResultListener<T> listener) {

        if (isConnectionAvailable()) {

            doAsynchronousCall(apiKey, new NetworkResultListener<T>() {

                @Override
                public void success(T result) {

                    if (listener != null) {
                        listener.success(result);
                    }

                }

                @Override
                public void onFailure(RetrofitError error) {

                    retrofit.client.Response r = error.getResponse();

                    if (r != null) {

                        int statusCode = r.getStatus();

                        if (statusCode == 400) {

                            TypedInput body = r.getBody();

                            new DLog("GenericAuthenticationServiceRequest").error("Client Bad Request " + readInputStream(body), error);

                        }

                        if (getRetryPolicy().shouldRetryForStatusCode(statusCode) && getRetryPolicy().retry()) {

                            try {
                                Thread.sleep(getRetryPolicy().getDelayBeforeNextRetry());
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            performAsynchronous(apiKey, listener);

                        } else if (statusCode == 401) {

                            DonkyAccountController.getInstance().reRegisterWithSameUserDetails(new DonkyListener() {

                                @Override
                                public void success() {

                                    performAsynchronous(apiKey, listener);

                                }

                                @Override
                                public void error(DonkyException donkyException, Map<String, String> validationErrors) {

                                    if (listener != null) {
                                        listener.error(donkyException, null);
                                    }
                                }
                            });

                        } else if (statusCode == 403) {

                            DonkyAccountController.getInstance().setSuspended(true);

                            listener.userSuspended();

                        } else {

                            DonkyException donkyException = new DonkyException("Error performing network call. " + error.getResponse().getReason());

                            donkyException.initCause(error);

                            if (listener != null) {
                                listener.error(donkyException, null);
                            }

                        }

                    } else {

                        DonkyException donkyException = new DonkyException("Error performing network call. Null response.");

                        donkyException.initCause(error);

                        if (listener != null) {
                            listener.error(donkyException, null);
                        }

                    }
                }
            });

        } else {

            doStartListenForConnectionRestored();
            DonkyException donkyException = new DonkyException("Internet connection not available.");

            if (listener != null) {
                listener.error(donkyException, null);
            }

        }
    }

    /**
     * Reads input stream from service response and decodes it to string.
     *
     * @param body Typed input stream to decode.
     * @return String decoded from typed input stream.
     */
    private String readInputStream(TypedInput body) {

        try {

            BufferedReader reader = new BufferedReader(new InputStreamReader(body.in()));
            StringBuilder out = new StringBuilder();
            String newLine = System.getProperty("line.separator");
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
                out.append(newLine);
            }

            return out.toString();

        } catch (IOException e) {

            new DLog("GenericAuthenticationServiceRequest").error("Client Bad Request and response body processing", e);

            return null;
        }
    }
}