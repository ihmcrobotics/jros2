
package us.ihmc.fastddsjava.profiles.gen;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for threadSettingsWithPortType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="threadSettingsWithPortType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{http://www.eprosima.com}threadSettingsType"&gt;
 *       &lt;attribute name="port" use="required" type="{http://www.eprosima.com}uint32" /&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "threadSettingsWithPortType")
public class ThreadSettingsWithPortType
    extends ThreadSettingsType
{

    @XmlAttribute(name = "port", required = true)
    protected long port;

    /**
     * Gets the value of the port property.
     * 
     */
    public long getPort() {
        return port;
    }

    /**
     * Sets the value of the port property.
     * 
     */
    public void setPort(long value) {
        this.port = value;
    }

}
