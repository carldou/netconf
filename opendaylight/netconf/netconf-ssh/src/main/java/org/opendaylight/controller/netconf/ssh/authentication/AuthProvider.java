/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.netconf.ssh.authentication;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import org.opendaylight.controller.sal.authorization.AuthResultEnum;
import org.opendaylight.controller.usermanager.IUserManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthProvider {
    private static final Logger logger = LoggerFactory.getLogger(AuthProvider.class);

    private final String pem;
    private IUserManager nullableUserManager;

    public AuthProvider(String pemCertificate, final BundleContext bundleContext) {
        checkNotNull(pemCertificate, "Parameter 'pemCertificate' is null");
        pem = pemCertificate;

        ServiceTrackerCustomizer<IUserManager, IUserManager> customizer = new ServiceTrackerCustomizer<IUserManager, IUserManager>() {
            @Override
            public IUserManager addingService(final ServiceReference<IUserManager> reference) {
                logger.trace("Service {} added", reference);
                nullableUserManager = bundleContext.getService(reference);
                return nullableUserManager;
            }

            @Override
            public void modifiedService(final ServiceReference<IUserManager> reference, final IUserManager service) {
                logger.trace("Replacing modified service {} in netconf SSH.", reference);
                nullableUserManager = service;
            }

            @Override
            public void removedService(final ServiceReference<IUserManager> reference, final IUserManager service) {
                logger.trace("Removing service {} from netconf SSH. " +
                        "SSH won't authenticate users until IUserManager service will be started.", reference);
                synchronized (AuthProvider.this) {
                    nullableUserManager = null;
                }
            }
        };
        ServiceTracker<IUserManager, IUserManager> listenerTracker = new ServiceTracker<>(bundleContext, IUserManager.class, customizer);
        listenerTracker.open();
    }

    /**
     * Authenticate user. This implementation tracks IUserManager and delegates the decision to it. If the service is not
     * available, IllegalStateException is thrown.
     */
    public synchronized boolean authenticated(String username, String password) {
        if (nullableUserManager == null) {
            logger.warn("Cannot authenticate user '{}', user manager service is missing", username);
            throw new IllegalStateException("User manager service is not available");
        }
        AuthResultEnum authResult = nullableUserManager.authenticate(username, password);
        logger.debug("Authentication result for user '{}' : {}", username, authResult);
        return authResult.equals(AuthResultEnum.AUTH_ACCEPT) || authResult.equals(AuthResultEnum.AUTH_ACCEPT_LOC);
    }

    public char[] getPEMAsCharArray() {
        return pem.toCharArray();
    }

    @VisibleForTesting
    void setNullableUserManager(IUserManager nullableUserManager) {
        this.nullableUserManager = nullableUserManager;
    }
}
