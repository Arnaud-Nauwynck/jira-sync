package fr.an.jira.util;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * utility class methods for List, stream
 */
public class LsUtil {

    private LsUtil() {}

    public static <TSrc,TDest>List<TDest> map(Collection<TSrc> src, Function<TSrc,TDest> mapFunc) {
        return src.stream().map(mapFunc).collect(Collectors.toList());
    }

}
