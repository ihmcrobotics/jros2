
package us.ihmc.fastddsjava.profiles.gen;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for tlsConfigType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="tlsConfigType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all minOccurs="0"&gt;
 *         &lt;element name="password" type="{http://www.eprosima.com}string" minOccurs="0"/&gt;
 *         &lt;element name="private_key_file" type="{http://www.eprosima.com}string" minOccurs="0"/&gt;
 *         &lt;element name="rsa_private_key_file" type="{http://www.eprosima.com}string" minOccurs="0"/&gt;
 *         &lt;element name="cert_chain_file" type="{http://www.eprosima.com}string" minOccurs="0"/&gt;
 *         &lt;element name="tmp_dh_file" type="{http://www.eprosima.com}string" minOccurs="0"/&gt;
 *         &lt;element name="verify_file" type="{http://www.eprosima.com}string" minOccurs="0"/&gt;
 *         &lt;element name="verify_mode" minOccurs="0"&gt;
 *           &lt;complexType&gt;
 *             &lt;complexContent&gt;
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                 &lt;sequence&gt;
 *                   &lt;element name="verify" maxOccurs="unbounded" minOccurs="0"&gt;
 *                     &lt;simpleType&gt;
 *                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *                         &lt;enumeration value="VERIFY_NONE"/&gt;
 *                         &lt;enumeration value="VERIFY_PEER"/&gt;
 *                         &lt;enumeration value="VERIFY_FAIL_IF_NO_PEER_CERT"/&gt;
 *                         &lt;enumeration value="VERIFY_CLIENT_ONCE"/&gt;
 *                       &lt;/restriction&gt;
 *                     &lt;/simpleType&gt;
 *                   &lt;/element&gt;
 *                 &lt;/sequence&gt;
 *               &lt;/restriction&gt;
 *             &lt;/complexContent&gt;
 *           &lt;/complexType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="verify_paths" minOccurs="0"&gt;
 *           &lt;complexType&gt;
 *             &lt;complexContent&gt;
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                 &lt;sequence&gt;
 *                   &lt;element name="verify_path" type="{http://www.eprosima.com}string" maxOccurs="unbounded" minOccurs="0"/&gt;
 *                 &lt;/sequence&gt;
 *               &lt;/restriction&gt;
 *             &lt;/complexContent&gt;
 *           &lt;/complexType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="verify_depth" type="{http://www.eprosima.com}int32" minOccurs="0"/&gt;
 *         &lt;element name="default_verify_path" type="{http://www.eprosima.com}boolean" minOccurs="0"/&gt;
 *         &lt;element name="options" minOccurs="0"&gt;
 *           &lt;complexType&gt;
 *             &lt;complexContent&gt;
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                 &lt;sequence&gt;
 *                   &lt;element name="option" maxOccurs="unbounded" minOccurs="0"&gt;
 *                     &lt;simpleType&gt;
 *                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *                         &lt;enumeration value="DEFAULT_WORKAROUNDS"/&gt;
 *                         &lt;enumeration value="NO_COMPRESSION"/&gt;
 *                         &lt;enumeration value="NO_SSLV2"/&gt;
 *                         &lt;enumeration value="NO_SSLV3"/&gt;
 *                         &lt;enumeration value="NO_TLSV1"/&gt;
 *                         &lt;enumeration value="NO_TLSV1_1"/&gt;
 *                         &lt;enumeration value="NO_TLSV1_2"/&gt;
 *                         &lt;enumeration value="NO_TLSV1_3"/&gt;
 *                         &lt;enumeration value="SINGLE_DH_USE"/&gt;
 *                       &lt;/restriction&gt;
 *                     &lt;/simpleType&gt;
 *                   &lt;/element&gt;
 *                 &lt;/sequence&gt;
 *               &lt;/restriction&gt;
 *             &lt;/complexContent&gt;
 *           &lt;/complexType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="handshake_role" minOccurs="0"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *               &lt;enumeration value="DEFAULT"/&gt;
 *               &lt;enumeration value="CLIENT"/&gt;
 *               &lt;enumeration value="SERVER"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="server_name" type="{http://www.eprosima.com}string" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "tlsConfigType", propOrder = {

})
public class TlsConfigType {

    protected String password;
    @XmlElement(name = "private_key_file")
    protected String privateKeyFile;
    @XmlElement(name = "rsa_private_key_file")
    protected String rsaPrivateKeyFile;
    @XmlElement(name = "cert_chain_file")
    protected String certChainFile;
    @XmlElement(name = "tmp_dh_file")
    protected String tmpDhFile;
    @XmlElement(name = "verify_file")
    protected String verifyFile;
    @XmlElement(name = "verify_mode")
    protected TlsConfigType.VerifyMode verifyMode;
    @XmlElement(name = "verify_paths")
    protected TlsConfigType.VerifyPaths verifyPaths;
    @XmlElement(name = "verify_depth")
    protected Integer verifyDepth;
    @XmlElement(name = "default_verify_path")
    protected Boolean defaultVerifyPath;
    protected TlsConfigType.Options options;
    @XmlElement(name = "handshake_role")
    protected String handshakeRole;
    @XmlElement(name = "server_name")
    protected String serverName;

    /**
     * Gets the value of the password property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the value of the password property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPassword(String value) {
        this.password = value;
    }

    /**
     * Gets the value of the privateKeyFile property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    /**
     * Sets the value of the privateKeyFile property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPrivateKeyFile(String value) {
        this.privateKeyFile = value;
    }

    /**
     * Gets the value of the rsaPrivateKeyFile property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRsaPrivateKeyFile() {
        return rsaPrivateKeyFile;
    }

    /**
     * Sets the value of the rsaPrivateKeyFile property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRsaPrivateKeyFile(String value) {
        this.rsaPrivateKeyFile = value;
    }

    /**
     * Gets the value of the certChainFile property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCertChainFile() {
        return certChainFile;
    }

    /**
     * Sets the value of the certChainFile property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCertChainFile(String value) {
        this.certChainFile = value;
    }

    /**
     * Gets the value of the tmpDhFile property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTmpDhFile() {
        return tmpDhFile;
    }

    /**
     * Sets the value of the tmpDhFile property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTmpDhFile(String value) {
        this.tmpDhFile = value;
    }

    /**
     * Gets the value of the verifyFile property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVerifyFile() {
        return verifyFile;
    }

    /**
     * Sets the value of the verifyFile property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVerifyFile(String value) {
        this.verifyFile = value;
    }

    /**
     * Gets the value of the verifyMode property.
     * 
     * @return
     *     possible object is
     *     {@link TlsConfigType.VerifyMode }
     *     
     */
    public TlsConfigType.VerifyMode getVerifyMode() {
        return verifyMode;
    }

    /**
     * Sets the value of the verifyMode property.
     * 
     * @param value
     *     allowed object is
     *     {@link TlsConfigType.VerifyMode }
     *     
     */
    public void setVerifyMode(TlsConfigType.VerifyMode value) {
        this.verifyMode = value;
    }

    /**
     * Gets the value of the verifyPaths property.
     * 
     * @return
     *     possible object is
     *     {@link TlsConfigType.VerifyPaths }
     *     
     */
    public TlsConfigType.VerifyPaths getVerifyPaths() {
        return verifyPaths;
    }

    /**
     * Sets the value of the verifyPaths property.
     * 
     * @param value
     *     allowed object is
     *     {@link TlsConfigType.VerifyPaths }
     *     
     */
    public void setVerifyPaths(TlsConfigType.VerifyPaths value) {
        this.verifyPaths = value;
    }

    /**
     * Gets the value of the verifyDepth property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getVerifyDepth() {
        return verifyDepth;
    }

    /**
     * Sets the value of the verifyDepth property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setVerifyDepth(Integer value) {
        this.verifyDepth = value;
    }

    /**
     * Gets the value of the defaultVerifyPath property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isDefaultVerifyPath() {
        return defaultVerifyPath;
    }

    /**
     * Sets the value of the defaultVerifyPath property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setDefaultVerifyPath(Boolean value) {
        this.defaultVerifyPath = value;
    }

    /**
     * Gets the value of the options property.
     * 
     * @return
     *     possible object is
     *     {@link TlsConfigType.Options }
     *     
     */
    public TlsConfigType.Options getOptions() {
        return options;
    }

    /**
     * Sets the value of the options property.
     * 
     * @param value
     *     allowed object is
     *     {@link TlsConfigType.Options }
     *     
     */
    public void setOptions(TlsConfigType.Options value) {
        this.options = value;
    }

    /**
     * Gets the value of the handshakeRole property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHandshakeRole() {
        return handshakeRole;
    }

    /**
     * Sets the value of the handshakeRole property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHandshakeRole(String value) {
        this.handshakeRole = value;
    }

    /**
     * Gets the value of the serverName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Sets the value of the serverName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setServerName(String value) {
        this.serverName = value;
    }


    /**
     * <p>Java class for anonymous complex type.
     * 
     * <p>The following schema fragment specifies the expected content contained within this class.
     * 
     * <pre>
     * &lt;complexType&gt;
     *   &lt;complexContent&gt;
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *       &lt;sequence&gt;
     *         &lt;element name="option" maxOccurs="unbounded" minOccurs="0"&gt;
     *           &lt;simpleType&gt;
     *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
     *               &lt;enumeration value="DEFAULT_WORKAROUNDS"/&gt;
     *               &lt;enumeration value="NO_COMPRESSION"/&gt;
     *               &lt;enumeration value="NO_SSLV2"/&gt;
     *               &lt;enumeration value="NO_SSLV3"/&gt;
     *               &lt;enumeration value="NO_TLSV1"/&gt;
     *               &lt;enumeration value="NO_TLSV1_1"/&gt;
     *               &lt;enumeration value="NO_TLSV1_2"/&gt;
     *               &lt;enumeration value="NO_TLSV1_3"/&gt;
     *               &lt;enumeration value="SINGLE_DH_USE"/&gt;
     *             &lt;/restriction&gt;
     *           &lt;/simpleType&gt;
     *         &lt;/element&gt;
     *       &lt;/sequence&gt;
     *     &lt;/restriction&gt;
     *   &lt;/complexContent&gt;
     * &lt;/complexType&gt;
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "option"
    })
    public static class Options {

        protected List<String> option;

        /**
         * Gets the value of the option property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the option property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getOption().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link String }
         * 
         * 
         */
        public List<String> getOption() {
            if (option == null) {
                option = new ArrayList<String>();
            }
            return this.option;
        }

    }


    /**
     * <p>Java class for anonymous complex type.
     * 
     * <p>The following schema fragment specifies the expected content contained within this class.
     * 
     * <pre>
     * &lt;complexType&gt;
     *   &lt;complexContent&gt;
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *       &lt;sequence&gt;
     *         &lt;element name="verify" maxOccurs="unbounded" minOccurs="0"&gt;
     *           &lt;simpleType&gt;
     *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
     *               &lt;enumeration value="VERIFY_NONE"/&gt;
     *               &lt;enumeration value="VERIFY_PEER"/&gt;
     *               &lt;enumeration value="VERIFY_FAIL_IF_NO_PEER_CERT"/&gt;
     *               &lt;enumeration value="VERIFY_CLIENT_ONCE"/&gt;
     *             &lt;/restriction&gt;
     *           &lt;/simpleType&gt;
     *         &lt;/element&gt;
     *       &lt;/sequence&gt;
     *     &lt;/restriction&gt;
     *   &lt;/complexContent&gt;
     * &lt;/complexType&gt;
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "verify"
    })
    public static class VerifyMode {

        protected List<String> verify;

        /**
         * Gets the value of the verify property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the verify property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getVerify().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link String }
         * 
         * 
         */
        public List<String> getVerify() {
            if (verify == null) {
                verify = new ArrayList<String>();
            }
            return this.verify;
        }

    }


    /**
     * <p>Java class for anonymous complex type.
     * 
     * <p>The following schema fragment specifies the expected content contained within this class.
     * 
     * <pre>
     * &lt;complexType&gt;
     *   &lt;complexContent&gt;
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *       &lt;sequence&gt;
     *         &lt;element name="verify_path" type="{http://www.eprosima.com}string" maxOccurs="unbounded" minOccurs="0"/&gt;
     *       &lt;/sequence&gt;
     *     &lt;/restriction&gt;
     *   &lt;/complexContent&gt;
     * &lt;/complexType&gt;
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "verifyPath"
    })
    public static class VerifyPaths {

        @XmlElement(name = "verify_path")
        protected List<String> verifyPath;

        /**
         * Gets the value of the verifyPath property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the verifyPath property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getVerifyPath().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link String }
         * 
         * 
         */
        public List<String> getVerifyPath() {
            if (verifyPath == null) {
                verifyPath = new ArrayList<String>();
            }
            return this.verifyPath;
        }

    }

}
