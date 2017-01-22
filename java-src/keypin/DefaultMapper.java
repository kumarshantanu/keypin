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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

public class DefaultMapper implements Mapper {

    private final Function realizer;

    public DefaultMapper() {
        this.realizer = Function.IDENTITY;
    }

    public DefaultMapper(Function realizer) {
        this.realizer = realizer;
    }

    @Override
    public Collection<?> processCollection(Collection<?> coll, Function itemProcessor) {
        // treat all non-sets like lists
        final Collection<Object> result = (coll instanceof Set<?>)? new LinkedHashSet<>(): new LinkedList<>();
        for (Object item: coll) {
            result.add(itemProcessor.execute(item));
        }
        return Collections.unmodifiableCollection(result);
    }

    @Override
    public Map<?, ?> processMap(Map<?, ?> dict, Function keyProcessor, Function valueProcessor) {
        final Map<Object, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry: dict.entrySet()) {
            result.put(keyProcessor.execute(entry.getKey()), valueProcessor.execute(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public Object processValue(Object value) {
        return realizer.execute(value);
    }

}
