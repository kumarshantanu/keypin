/* Copyright (c) Shantanu Kumar. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */


package keypin;

import java.util.Collection;
import java.util.Map;

public interface Mapper {

    public static final Mapper NOP = new Mapper() {
        @Override
        public Map<?, ?> processMap(Map<?, ?> dict, Function keyProcessor, Function valueProcessor) { return dict; }

        @Override
        public Collection<?> processCollection(Collection<?> coll, Function itemProcessor) { return coll; }

        @Override
        public Object processValue(Object value) { return value; }
    };

    public static final Mapper DEFAULT = new DefaultMapper();

    Collection<?> processCollection(Collection<?> coll, Function itemProcessor);

    Map<?, ?> processMap(Map<?, ?> dict, Function keyProcessor, Function valueProcessor);

    Object processValue(Object value);

}
