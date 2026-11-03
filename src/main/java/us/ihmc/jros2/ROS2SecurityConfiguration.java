/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.jros2;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * SROS2 (DDS-Security) configuration of a participant, resolved like rcl ({@code security.c}) and rmw_fastrtps (Jazzy):
 * <ul>
 *    <li>Security is requested only if {@code ROS_SECURITY_ENABLE} is exactly "true".</li>
 *    <li>The strategy is Enforce only if {@code ROS_SECURITY_STRATEGY} is exactly "Enforce"; anything else is Permissive.</li>
 *    <li>The security directory is {@code <ROS_SECURITY_KEYSTORE>/enclaves/<enclave without the leading '/'>} ({@code /} maps to
 *    {@code <keystore>/enclaves}); {@code ROS_SECURITY_ENCLAVE_OVERRIDE}, if set, replaces the enclave for this lookup only.</li>
 *    <li>Required files: identity_ca.cert, cert, key and permissions_ca.cert ({@code .p11} with a {@code pkcs11:} URI, or
 *    {@code .pem}), governance.p7s and permissions.p7s; crl.pem is optional.</li>
 *    <li>Enforce fails closed with an {@link IllegalStateException} when the keystore is not set, the directory or a file is
 *    missing, or the loaded Fast-DDS natives are built without DDS-Security. Permissive logs a warning and runs unsecured.</li>
 *    <li>The participant gets the exact {@code dds.sec.*} properties rmw_fastrtps sets, with {@code file://} URIs, plus the
 *    security logging properties from {@code ROS_SECURITY_LOG_FILE}, {@code ROS_SECURITY_LOG_PUBLISH} and
 *    {@code ROS_SECURITY_LOG_VERBOSITY}.</li>
 *    <li>For ROS 2 Foxy, Galactic and Humble peers the certificate algorithms are announced with their legacy names, see
 *    {@link #useLegacyAlgorithmNames(String, String)}.</li>
 * </ul>
 * Remember that the governance and permissions of a keystore are domain specific, and that permissions grant the ROS-mangled
 * topic names ({@code rt/...}) and {@code ros_discovery_info}.
 */
final class ROS2SecurityConfiguration
{
   static final String FILE_URI_PREFIX = "file://";
   static final String PKCS11_PREFIX = "pkcs11:";
   /** Fast-DDS properties that make the authentication and access control plugins announce the legacy algorithm names. */
   static final String LEGACY_AUTHENTICATION_ALGORITHMS = "dds.sec.auth.builtin.PKI-DH.transmit_algorithms_as_legacy";
   static final String LEGACY_ACCESS_CONTROL_ALGORITHMS = "dds.sec.access.builtin.Access-Permissions.transmit_algorithms_as_legacy";

   /** Outcome of the resolution. */
   enum Status
   {
      /** ROS_SECURITY_ENABLE is not "true". */
      DISABLED,
      /** Security requested and configured; the participant gets the DDS-Security properties. */
      ENABLED,
      /** Security requested with the Permissive strategy but not possible; the participant runs unsecured. */
      PERMISSIVE_UNSECURED
   }

   private final Status status;
   private final boolean enforce;
   private final File securityDirectory;
   private final Map<String, String> properties;
   private final List<String> warnings;

   private ROS2SecurityConfiguration(Status status, boolean enforce, File securityDirectory, Map<String, String> properties, List<String> warnings)
   {
      this.status = status;
      this.enforce = enforce;
      this.securityDirectory = securityDirectory;
      this.properties = Collections.unmodifiableMap(properties);
      this.warnings = Collections.unmodifiableList(warnings);
   }

   Status getStatus()
   {
      return status;
   }

   /** @return whether the Enforce strategy is selected (only meaningful when security is requested) */
   boolean isEnforce()
   {
      return enforce;
   }

   /** @return the enclave security directory, or null if it was not found or security is disabled */
   File getSecurityDirectory()
   {
      return securityDirectory;
   }

   /** @return the Fast-DDS participant properties, in rmw_fastrtps order; empty unless {@link Status#ENABLED} */
   Map<String, String> getProperties()
   {
      return properties;
   }

   List<String> getWarnings()
   {
      return warnings;
   }

   /**
    * Resolves the security configuration.
    *
    * @param enclave           the participant's enclave (already validated)
    * @param settings          raw settings lookup
    * @param nativeHasSecurity whether the loaded Fast-DDS natives support DDS-Security
    * @throws IllegalStateException    under Enforce when security cannot be applied
    * @throws IllegalArgumentException when a security logging setting is invalid (rmw_fastrtps fails as well)
    */
   static ROS2SecurityConfiguration resolve(String enclave, Function<jros2Settings.Key, String> settings, BooleanSupplier nativeHasSecurity)
   {
      List<String> warnings = new ArrayList<>();
      if (!"true".equals(settings.apply(jros2Settings.Key.SECURITY_ENABLE)))
      {
         return new ROS2SecurityConfiguration(Status.DISABLED, false, null, new LinkedHashMap<>(), warnings);
      }

      boolean enforce = "Enforce".equals(settings.apply(jros2Settings.Key.SECURITY_STRATEGY));

      String keystore = settings.apply(jros2Settings.Key.SECURITY_KEYSTORE);
      if (keystore == null || keystore.isEmpty())
      {
         return failOrWarn(enforce, "SECURITY ERROR: ROS_SECURITY_ENABLE is 'true' but ROS_SECURITY_KEYSTORE is not set", warnings);
      }

      String override = settings.apply(jros2Settings.Key.SECURITY_ENCLAVE_OVERRIDE);
      String lookupEnclave = override == null || override.isEmpty() ? enclave : override;
      File directory = securityDirectory(keystore, lookupEnclave);
      if (!directory.isDirectory())
      {
         return failOrWarn(enforce, "SECURITY ERROR: directory '" + directory.getPath() + "' does not exist.", warnings);
      }

      Map<String, String> files = new LinkedHashMap<>();
      List<String> missing = new ArrayList<>();
      findSecurityFile(directory, "identity_ca.cert", true, "IDENTITY_CA", files, missing);
      findSecurityFile(directory, "cert", true, "CERTIFICATE", files, missing);
      findSecurityFile(directory, "key", true, "PRIVATE_KEY", files, missing);
      findSecurityFile(directory, "permissions_ca.cert", true, "PERMISSIONS_CA", files, missing);
      findSecurityFile(directory, "governance", false, "GOVERNANCE", files, missing);
      findSecurityFile(directory, "permissions", false, "PERMISSIONS", files, missing);
      if (!missing.isEmpty())
      {
         return failOrWarn(enforce, "couldn't find all security files in '" + directory.getPath() + "' (missing: " + String.join(", ", missing) + ")", warnings);
      }

      if (!nativeHasSecurity.getAsBoolean())
      {
         return failOrWarn(enforce,
                           "SROS2 security is enabled but the loaded Fast-DDS native library was built without DDS-Security (-DSECURITY=ON); "
                           + "refusing to communicate unsecured",
                           warnings);
      }

      Map<String, String> properties = new LinkedHashMap<>();
      properties.put("dds.sec.auth.plugin", "builtin.PKI-DH");
      properties.put("dds.sec.auth.builtin.PKI-DH.identity_ca", files.get("IDENTITY_CA"));
      properties.put("dds.sec.auth.builtin.PKI-DH.identity_certificate", files.get("CERTIFICATE"));
      properties.put("dds.sec.auth.builtin.PKI-DH.private_key", files.get("PRIVATE_KEY"));
      properties.put("dds.sec.crypto.plugin", "builtin.AES-GCM-GMAC");
      properties.put("dds.sec.access.plugin", "builtin.Access-Permissions");
      properties.put("dds.sec.access.builtin.Access-Permissions.permissions_ca", files.get("PERMISSIONS_CA"));
      properties.put("dds.sec.access.builtin.Access-Permissions.governance", files.get("GOVERNANCE"));
      properties.put("dds.sec.access.builtin.Access-Permissions.permissions", files.get("PERMISSIONS"));
      File crl = new File(directory, "crl.pem");
      if (crl.isFile())
      {
         properties.put("dds.sec.auth.builtin.PKI-DH.identity_crl", fileUri(crl));
      }
      if (useLegacyAlgorithmNames(settings.apply(jros2Settings.Key.SECURITY_LEGACY_ALGORITHMS), settings.apply(jros2Settings.Key.ROS_DISTRO)))
      {
         properties.put(LEGACY_AUTHENTICATION_ALGORITHMS, "true");
         properties.put(LEGACY_ACCESS_CONTROL_ALGORITHMS, "true");
      }
      addLoggingProperties(settings, properties);

      return new ROS2SecurityConfiguration(Status.ENABLED, enforce, directory, properties, warnings);
   }

   /**
    * Whether the participant announces the algorithms of its identity and permissions CA certificates with the legacy names
    * ("ECDSA-SHA256", "RSASSA-PSS-SHA256") instead of the DDS-Security 1.1 names ("EC-prime256v1", "RSA-2048") that Fast-DDS 3
    * sends by default. The Fast-DDS of ROS 2 Foxy, Galactic and Humble (2.1 to 2.6) only accepts the legacy names and rejects a
    * peer that sends the others ("Remote participant PermissionsCA algorithm differs from local"), so no secure communication
    * with those distros is possible without them. Fast-DDS 2.14 (Jazzy) and newer accept both forms.
    *
    * @param setting   the {@code JROS2_SECURITY_LEGACY_ALGORITHMS} setting: AUTO (or unset), true or false, case-insensitive
    * @param rosDistro the ROS_DISTRO setting; AUTO uses the legacy names for foxy, galactic and humble
    * @throws IllegalArgumentException if the setting is not AUTO, true or false
    */
   static boolean useLegacyAlgorithmNames(String setting, String rosDistro)
   {
      String value = setting == null ? "" : setting.trim().toUpperCase(Locale.ROOT);
      switch (value)
      {
         case "TRUE":
            return true;
         case "FALSE":
            return false;
         case "":
         case "AUTO":
            break;
         default:
            throw new IllegalArgumentException("JROS2_SECURITY_LEGACY_ALGORITHMS is not valid: '" + setting + "' (use AUTO, true or false)");
      }
      String distro = rosDistro == null ? "" : rosDistro.trim().toLowerCase(Locale.ROOT);
      return distro.equals("foxy") || distro.equals("galactic") || distro.equals("humble");
   }

   private static ROS2SecurityConfiguration failOrWarn(boolean enforce, String message, List<String> warnings)
   {
      if (enforce)
      {
         throw new IllegalStateException(message + " (ROS_SECURITY_STRATEGY=Enforce)");
      }
      warnings.add(message + "; ROS_SECURITY_STRATEGY is not 'Enforce', so the node runs WITHOUT security");
      return new ROS2SecurityConfiguration(Status.PERMISSIVE_UNSECURED, false, null, new LinkedHashMap<>(), warnings);
   }

   /**
    * rcl {@code exact_match_lookup}: {@code <keystore>/enclaves} joined with the enclave without its leading '/'.
    */
   static File securityDirectory(String keystore, String enclave)
   {
      File enclaves = new File(keystore, "enclaves");
      if (enclave.equals("/") || enclave.isEmpty())
      {
         return enclaves;
      }
      String relative = enclave.startsWith("/") ? enclave.substring(1) : enclave;
      return new File(enclaves, relative.replace('/', File.separatorChar));
   }

   /**
    * rmw_dds_common {@code get_security_files}: a {@code .p11} file whose first token starts with {@code pkcs11:} wins (when
    * allowed), otherwise the {@code .pem} (or {@code .p7s}) file must be a regular file.
    */
   private static void findSecurityFile(File directory, String baseName, boolean pemWithPkcs11, String key, Map<String, String> files, List<String> missing)
   {
      if (pemWithPkcs11)
      {
         String pkcs11Uri = readPkcs11Uri(new File(directory, baseName + ".p11"));
         if (pkcs11Uri != null)
         {
            files.put(key, pkcs11Uri);
            return;
         }
         File pem = new File(directory, baseName + ".pem");
         if (pem.isFile())
         {
            files.put(key, fileUri(pem));
            return;
         }
         missing.add(baseName + ".pem");
      }
      else
      {
         File p7s = new File(directory, baseName + ".p7s");
         if (p7s.isFile())
         {
            files.put(key, fileUri(p7s));
            return;
         }
         missing.add(baseName + ".p7s");
      }
   }

   private static String readPkcs11Uri(File file)
   {
      if (!file.isFile())
      {
         return null;
      }
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)))
      {
         StringBuilder content = new StringBuilder();
         String line;
         while ((line = reader.readLine()) != null)
         {
            content.append(line).append('\n');
         }
         String trimmed = content.toString().trim();
         if (trimmed.isEmpty())
         {
            return null;
         }
         // operator>> reads the first whitespace-separated token
         String token = trimmed.split("\\s+", 2)[0];
         return token.startsWith(PKCS11_PREFIX) ? token : null;
      }
      catch (IOException e)
      {
         return null;
      }
   }

   /**
    * {@code "file://" + std::filesystem::path(...).generic_string()}: not canonicalized (symlinks stay), '/' separators.
    */
   static String fileUri(File file)
   {
      return FILE_URI_PREFIX + file.getPath().replace(File.separatorChar, '/');
   }

   /**
    * rmw_fastrtps {@code apply_security_logging_configuration}.
    */
   private static void addLoggingProperties(Function<jros2Settings.Key, String> settings, Map<String, String> properties)
   {
      boolean any = false;

      String logFile = settings.apply(jros2Settings.Key.SECURITY_LOG_FILE);
      if (logFile != null && !logFile.isEmpty())
      {
         properties.put("dds.sec.log.builtin.DDS_LogTopic.log_file", logFile);
         any = true;
      }

      String publish = settings.apply(jros2Settings.Key.SECURITY_LOG_PUBLISH);
      if (publish != null && !publish.isEmpty())
      {
         if (!publish.equals("true") && !publish.equals("false"))
         {
            throw new IllegalArgumentException("ROS_SECURITY_LOG_PUBLISH is not valid: '" + publish + "' is not a supported value (use 'true' or 'false')");
         }
         properties.put("dds.sec.log.builtin.DDS_LogTopic.distribute", publish);
         any = true;
      }

      String verbosity = settings.apply(jros2Settings.Key.SECURITY_LOG_VERBOSITY);
      if (verbosity != null && !verbosity.isEmpty())
      {
         String level = switch (verbosity.toUpperCase(Locale.ROOT))
         {
            case "FATAL" -> "EMERGENCY_LEVEL";
            case "ERROR" -> "ERROR_LEVEL";
            case "WARN", "WARNING" -> "WARNING_LEVEL";
            case "INFO" -> "INFORMATIONAL_LEVEL";
            case "DEBUG" -> "DEBUG_LEVEL";
            default -> throw new IllegalArgumentException("ROS_SECURITY_LOG_VERBOSITY is not valid: " + verbosity
                                                          + " is not a supported verbosity (use FATAL, ERROR, WARN, INFO, or DEBUG)");
         };
         properties.put("dds.sec.log.builtin.DDS_LogTopic.logging_level", level);
         any = true;
      }

      if (any)
      {
         properties.put("dds.sec.log.plugin", "builtin.DDS_LogTopic");
      }
   }

   /**
    * Validates an enclave name like rcl {@code rcl_validate_enclave_name} (namespace rules, at most 255 characters).
    *
    * @param enclave the enclave, e.g. "/talker_listener/talker"
    * @throws IllegalArgumentException if the name is invalid
    */
   static void validateEnclave(String enclave)
   {
      if (enclave == null || enclave.isEmpty())
      {
         throw new IllegalArgumentException("Invalid enclave name: it must not be empty");
      }
      if (enclave.length() > 255)
      {
         throw new IllegalArgumentException("Invalid enclave name '" + enclave + "': longer than 255 characters");
      }
      if (enclave.charAt(0) != '/')
      {
         throw new IllegalArgumentException("Invalid enclave name '" + enclave + "': it must be absolute (start with '/')");
      }
      if (enclave.length() > 1 && enclave.endsWith("/"))
      {
         throw new IllegalArgumentException("Invalid enclave name '" + enclave + "': it must not end with '/'");
      }
      for (int i = 0; i < enclave.length(); ++i)
      {
         char c = enclave.charAt(i);
         boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '/';
         if (!allowed)
         {
            throw new IllegalArgumentException("Invalid enclave name '" + enclave + "': character '" + c + "' at index " + i + " is not allowed");
         }
         if (c == '/' && i + 1 < enclave.length())
         {
            char next = enclave.charAt(i + 1);
            if (next == '/')
            {
               throw new IllegalArgumentException("Invalid enclave name '" + enclave + "': it must not contain '//'");
            }
            if (next >= '0' && next <= '9')
            {
               throw new IllegalArgumentException("Invalid enclave name '" + enclave + "': a name token must not start with a number");
            }
         }
      }
   }
}
