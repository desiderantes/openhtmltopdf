/*
 * {{{ header & license
 * Copyright (c) 2009 Patrick Wright
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package com.openhtmltopdf.util;

import java.util.Arrays;
import java.util.stream.Collectors;

public class ArrayUtil {
    public static String[] cloneOrEmpty(String[] source){
        return source == null ? Constants.EMPTY_STR_ARR : source.clone();
    }

    public static int[] cloneOrEmpty(int[] source) {
        return source == null ? Constants.EMPTY_INT_ARR : source.clone();
    }
    
    /**
     * Tests if left is equal to one of the rights. Also returns true if both are null.
     */
    @SafeVarargs
    public static <T> boolean isOneOf(T left, T... rights) {
        for (T candidate : rights) {
            if (candidate == null && left == null) {
                return true;
            } else if (left != null && left.equals(candidate)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Joins a string array, with the given separator.
     */
    public static String join(String[] partials, String separator) {
        return String.join(separator, partials);
    }
}
