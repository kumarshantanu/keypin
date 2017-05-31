/* Copyright (c) Shantanu Kumar. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */


package keypin;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Map;

public interface ConfigIO {

    public boolean canRead(String filename);

    public Map<?, ?> readConfig(InputStream in) throws Exception;

    public boolean canWrite(String filename);

    public void writeConfig (OutputStream out, Map<?, ?> config, boolean escape) throws Exception;

    public void writeConfig (Writer out, Map<?, ?> config, boolean escape) throws Exception;

}
