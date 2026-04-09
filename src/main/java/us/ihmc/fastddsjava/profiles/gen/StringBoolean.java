
package us.ihmc.fastddsjava.profiles.gen;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.dataformat.xml.annotation.*;




/**
 * <p>Java class for stringBoolean.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="stringBoolean"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="FALSE"/&gt;
 *     &lt;enumeration value="TRUE"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */

public enum StringBoolean {

    FALSE,
    TRUE;

    public String value() {
        return name();
    }

    public static StringBoolean fromValue(String v) {
        return valueOf(v);
    }

}
