package org.apache.logging.log4j.util;

import java.net.URL;
import java.security.Permission;
import java.util.Collection;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.apache.logging.log4j.spi.Provider;
import org.apache.logging.log4j.status.StatusLogger;
import org.osgi.framework.AdaptPermission;
import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

public class Activator implements BundleActivator, SynchronousBundleListener {
   private static final SecurityManager SECURITY_MANAGER = System.getSecurityManager();
   private static final Logger LOGGER = StatusLogger.getLogger();
   private boolean lockingProviderUtil;

   private static void checkPermission(final Permission permission) {
      if (SECURITY_MANAGER != null) {
         SECURITY_MANAGER.checkPermission(permission);
      }
   }

   private void loadProvider(final Bundle bundle) {
      if (bundle.getState() != 1) {
         try {
            checkPermission(new AdminPermission(bundle, "resource"));
            checkPermission(new AdaptPermission(BundleWiring.class.getName(), bundle, "adapt"));
            BundleContext bundleContext = bundle.getBundleContext();
            if (bundleContext == null) {
               LOGGER.debug("Bundle {} has no context (state={}), skipping loading provider", bundle.getSymbolicName(), this.toStateString(bundle.getState()));
            } else {
               this.loadProvider(bundleContext, (BundleWiring)bundle.adapt(BundleWiring.class));
            }
         } catch (SecurityException var3) {
            LOGGER.debug("Cannot access bundle [{}] contents. Ignoring.", bundle.getSymbolicName(), var3);
         } catch (Exception var4) {
            LOGGER.warn("Problem checking bundle {} for Log4j 2 provider.", bundle.getSymbolicName(), var4);
         }
      }
   }

   private String toStateString(final int state) {
      switch (state) {
         case 1:
            return "UNINSTALLED";
         case 2:
            return "INSTALLED";
         case 4:
            return "RESOLVED";
         case 8:
            return "STARTING";
         case 16:
            return "STOPPING";
         case 32:
            return "ACTIVE";
         default:
            return Integer.toString(state);
      }
   }

   private void loadProvider(final BundleContext bundleContext, final BundleWiring bundleWiring) {
      String filter = "(APIVersion>=2.6.0)";

      try {
         Collection<ServiceReference<Provider>> serviceReferences = bundleContext.getServiceReferences(Provider.class, "(APIVersion>=2.6.0)");
         Provider maxProvider = null;

         for (ServiceReference<Provider> serviceReference : serviceReferences) {
            Provider provider = (Provider)bundleContext.getService(serviceReference);
            if (maxProvider == null || provider.getPriority() > maxProvider.getPriority()) {
               maxProvider = provider;
            }
         }

         if (maxProvider != null) {
            ProviderUtil.addProvider(maxProvider);
         }
      } catch (InvalidSyntaxException var9) {
         LOGGER.error("Invalid service filter: (APIVersion>=2.6.0)", (Throwable)var9);
      }

      for (URL url : bundleWiring.findEntries("META-INF", "log4j-provider.properties", 0)) {
         ProviderUtil.loadProvider(url, bundleWiring.getClassLoader());
      }
   }

   public void start(final BundleContext bundleContext) throws Exception {
      ProviderUtil.STARTUP_LOCK.lock();
      this.lockingProviderUtil = true;
      BundleWiring self = (BundleWiring)bundleContext.getBundle().adapt(BundleWiring.class);

      for (BundleWire wire : self.getRequiredWires(LoggerContextFactory.class.getName())) {
         this.loadProvider(bundleContext, wire.getProviderWiring());
      }

      bundleContext.addBundleListener(this);
      Bundle[] bundles = bundleContext.getBundles();

      for (Bundle bundle : bundles) {
         this.loadProvider(bundle);
      }

      this.unlockIfReady();
   }

   private void unlockIfReady() {
      if (this.lockingProviderUtil && !ProviderUtil.PROVIDERS.isEmpty()) {
         ProviderUtil.STARTUP_LOCK.unlock();
         this.lockingProviderUtil = false;
      }
   }

   public void stop(final BundleContext bundleContext) throws Exception {
      bundleContext.removeBundleListener(this);
      this.unlockIfReady();
   }

   public void bundleChanged(final BundleEvent event) {
      switch (event.getType()) {
         case 2:
            this.loadProvider(event.getBundle());
            this.unlockIfReady();
      }
   }
}
