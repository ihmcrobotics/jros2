#!/bin/bash
#
#  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
# Creates a throw-away SROS2 keystore with the openssl CLI only (no ROS 2 installation needed), laid out like
#    ros2 security create_keystore <dir>
#    ros2 security create_enclave <dir> /talker_listener/talker
#    ros2 security create_enclave <dir> /talker_listener/listener
# for android/src/androidTest/java/us/ihmc/jros2/SROS2OnDeviceTest.java (and any other SROS2 test):
#  - one self-signed EC P-256 CA used as identity and permissions CA (private key in <dir>/private, never packaged),
#  - governance for domains 0-232 like sros2's default (no unauthenticated participants, join access control,
#    ENCRYPT discovery/liveliness/metadata/data, SIGN rtps), S/MIME signed by the CA,
#  - per enclave: key.pem, cert.pem (CN=<enclave>), permissions for rt/*, rq/*, rr/* and ros_discovery_info,
#    S/MIME signed, plus copies of governance.p7s and the CA certificates.
# Everything is valid from one day ago for 10 years.
#
# Usage: make-test-keystore.sh <keystore-dir> [enclave ...]   (default: /talker_listener/talker /talker_listener/listener)
set -euo pipefail

[ $# -ge 1 ] || { echo "usage: $0 <keystore-dir> [enclave ...]" >&2; exit 2; }
KEYSTORE=$1
shift
ENCLAVES=("$@")
[ ${#ENCLAVES[@]} -gt 0 ] || ENCLAVES=(/talker_listener/talker /talker_listener/listener)

# GNU date, with the BSD/macOS form as fallback.
NOT_BEFORE=$(date -u -d '-1 day' +%Y-%m-%dT%H:%M:%S 2> /dev/null || date -u -v-1d +%Y-%m-%dT%H:%M:%S)
NOT_AFTER=$(date -u -d '+3650 days' +%Y-%m-%dT%H:%M:%S 2> /dev/null || date -u -v+3650d +%Y-%m-%dT%H:%M:%S)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

rm -rf "$KEYSTORE"
mkdir -p "$KEYSTORE/private" "$KEYSTORE/public" "$KEYSTORE/enclaves"
CA_KEY=$KEYSTORE/private/ca.key.pem
CA_CERT=$KEYSTORE/public/ca.cert.pem

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$CA_KEY" 2> /dev/null
openssl req -x509 -new -key "$CA_KEY" -sha256 -days 3650 -subj "/CN=sros2testCA" \
   -addext "basicConstraints=critical,CA:TRUE,pathlen:1" -out "$CA_CERT"
cp "$CA_CERT" "$KEYSTORE/public/identity_ca.cert.pem"
cp "$CA_CERT" "$KEYSTORE/public/permissions_ca.cert.pem"

# Signs $1 (XML) into $2 (S/MIME, detached signature, text/plain content) with the CA, like sros2.
sign() {
   openssl smime -sign -text -md sha256 -in "$1" -out "$2" -signer "$CA_CERT" -inkey "$CA_KEY"
}

cat > "$KEYSTORE/enclaves/governance.xml" <<'EOF'
<dds xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://www.omg.org/spec/DDS-SECURITY/20170901/omg_shared_ca_governance.xsd">
    <domain_access_rules>
        <domain_rule>
            <domains>
              <id_range><min>0</min><max>232</max></id_range>
            </domains>
            <allow_unauthenticated_participants>false</allow_unauthenticated_participants>
            <enable_join_access_control>true</enable_join_access_control>
            <discovery_protection_kind>ENCRYPT</discovery_protection_kind>
            <liveliness_protection_kind>ENCRYPT</liveliness_protection_kind>
            <rtps_protection_kind>SIGN</rtps_protection_kind>
            <topic_access_rules>
                <topic_rule>
                    <topic_expression>*</topic_expression>
                    <enable_discovery_protection>true</enable_discovery_protection>
                    <enable_liveliness_protection>true</enable_liveliness_protection>
                    <enable_read_access_control>true</enable_read_access_control>
                    <enable_write_access_control>true</enable_write_access_control>
                    <metadata_protection_kind>ENCRYPT</metadata_protection_kind>
                    <data_protection_kind>ENCRYPT</data_protection_kind>
                </topic_rule>
            </topic_access_rules>
        </domain_rule>
    </domain_access_rules>
</dds>
EOF
sign "$KEYSTORE/enclaves/governance.xml" "$KEYSTORE/enclaves/governance.p7s"

topics() {
   local topic
   for topic in 'rq/*/_action/cancel_goalRequest' 'rq/*/_action/get_resultRequest' 'rq/*/_action/send_goalRequest' 'rq/*Request' \
                'rr/*/_action/cancel_goalReply' 'rr/*/_action/get_resultReply' 'rr/*/_action/send_goalReply' \
                'rt/*/_action/feedback' 'rt/*/_action/status' 'rr/*Reply' 'rt/*'; do
      echo "            <topic>$topic</topic>"
   done
}

serial=1
for enclave in "${ENCLAVES[@]}"; do
   case "$enclave" in
      /*) ;;
      *) echo "enclave names start with '/': $enclave" >&2; exit 2 ;;
   esac
   dir=$KEYSTORE/enclaves$enclave
   mkdir -p "$dir"
   serial=$((serial + 1))

   openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$dir/key.pem" 2> /dev/null
   # In -subj, '/' separates name components, so the slashes of the enclave name are escaped.
   openssl req -new -key "$dir/key.pem" -subj "/CN=${enclave//\//\\/}" -out "$WORK/request.csr"
   printf 'basicConstraints=CA:FALSE\n' > "$WORK/extensions.cnf"
   openssl x509 -req -in "$WORK/request.csr" -CA "$CA_CERT" -CAkey "$CA_KEY" -set_serial "$serial" -days 3650 -sha256 \
      -extfile "$WORK/extensions.cnf" -out "$dir/cert.pem" 2> /dev/null

   {
      cat <<EOF
<dds xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://www.omg.org/spec/DDS-SECURITY/20170901/omg_shared_ca_permissions.xsd">
  <permissions>
    <grant name="$enclave">
      <subject_name>CN=$enclave</subject_name>
      <validity>
        <not_before>$NOT_BEFORE</not_before>
        <not_after>$NOT_AFTER</not_after>
      </validity>
      <allow_rule>
        <domains>
          <id_range><min>0</min><max>232</max></id_range>
        </domains>
        <publish>
          <topics>
EOF
      topics
      cat <<EOF
          </topics>
        </publish>
        <subscribe>
          <topics>
EOF
      topics
      cat <<EOF
          </topics>
        </subscribe>
      </allow_rule>
      <allow_rule>
        <domains>
          <id_range><min>0</min><max>232</max></id_range>
        </domains>
        <publish>
          <topics>
            <topic>ros_discovery_info</topic>
          </topics>
        </publish>
        <subscribe>
          <topics>
            <topic>ros_discovery_info</topic>
          </topics>
        </subscribe>
      </allow_rule>
      <default>DENY</default>
    </grant>
  </permissions>
</dds>
EOF
   } > "$dir/permissions.xml"
   sign "$dir/permissions.xml" "$dir/permissions.p7s"
   cp "$KEYSTORE/enclaves/governance.p7s" "$dir/governance.p7s"
   cp "$CA_CERT" "$dir/identity_ca.cert.pem"
   cp "$CA_CERT" "$dir/permissions_ca.cert.pem"

   openssl verify -CAfile "$CA_CERT" "$dir/cert.pem" > /dev/null
   openssl smime -verify -text -in "$dir/permissions.p7s" -CAfile "$CA_CERT" -out /dev/null 2> /dev/null
   echo "enclave $enclave: $(openssl x509 -in "$dir/cert.pem" -noout -subject)"
done
openssl smime -verify -text -in "$KEYSTORE/enclaves/governance.p7s" -CAfile "$CA_CERT" -out /dev/null 2> /dev/null
echo "SROS2 test keystore written to $KEYSTORE (valid $NOT_BEFORE .. $NOT_AFTER)"
