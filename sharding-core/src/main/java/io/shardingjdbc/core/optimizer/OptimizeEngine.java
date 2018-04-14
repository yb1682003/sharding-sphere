/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingjdbc.core.optimizer;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import io.shardingjdbc.core.api.algorithm.sharding.ListShardingValue;
import io.shardingjdbc.core.api.algorithm.sharding.RangeShardingValue;
import io.shardingjdbc.core.constant.ShardingOperator;
import io.shardingjdbc.core.exception.ShardingJdbcException;
import io.shardingjdbc.core.parsing.parser.context.condition.AndCondition;
import io.shardingjdbc.core.parsing.parser.context.condition.Column;
import io.shardingjdbc.core.parsing.parser.context.condition.Condition;
import io.shardingjdbc.core.parsing.parser.context.condition.GeneratedKeyCondition;
import io.shardingjdbc.core.parsing.parser.context.condition.OrCondition;
import io.shardingjdbc.core.routing.sharding.AlwaysFalseShardingCondition;
import io.shardingjdbc.core.routing.sharding.GeneratedKey;
import io.shardingjdbc.core.routing.sharding.ShardingCondition;
import io.shardingjdbc.core.routing.sharding.ShardingConditions;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Optimize engine.
 *
 * @author maxiaoguang
 */
public class OptimizeEngine {
    
    /**
     * Get sharding conditions.
     *
     * @param orCondition or condition
     * @param parameters parameters
     * @param generatedKey generated key
     * @return sharding value
     */
    public ShardingConditions getOptimizeShardingConditions(final OrCondition orCondition, final List<Object> parameters, final GeneratedKey generatedKey) {
        ShardingConditions result = new ShardingConditions();
        if (orCondition.isEmpty()) {
            return result;
        }
        for (AndCondition each : orCondition.getAndConditions()) {
            result.add(getOptimizeShardingCondition(each, parameters, generatedKey));
        }
        return result;
    }
    
    private ShardingCondition getOptimizeShardingCondition(final AndCondition andCondition, final List<Object> parameters, final GeneratedKey generatedKey) {
        ShardingCondition result = new ShardingCondition();
        Map<Column, List<Condition>> conditionsMap = getConditionsMap(andCondition, generatedKey);
        for (Entry<Column, List<Condition>> entry : conditionsMap.entrySet()) {
            List<Comparable<?>> listValue = null;
            Range<Comparable<?>> rangeValue = null;
            for (Condition each : entry.getValue()) {
                List<Comparable<?>> values = getValues(each, parameters);
                if (ShardingOperator.EQUAL == each.getOperator() || ShardingOperator.IN == each.getOperator()) {
                    listValue = getOptimizeValue(values, listValue);
                    if (null == listValue) {
                        return new AlwaysFalseShardingCondition();
                    }
                }
                if (ShardingOperator.BETWEEN == each.getOperator()) {
                    try {
                        rangeValue = getOptimizeValue(Range.range(values.get(0), BoundType.CLOSED, values.get(1), BoundType.CLOSED), rangeValue);
                    } catch (IllegalArgumentException e) {
                        return new AlwaysFalseShardingCondition();
                    } catch (ClassCastException e) {
                        throw new ShardingJdbcException("Found different types for sharding value `%s`.", entry.getKey());
                    }
                }
            }
            if (null == listValue) {
                result.add(new RangeShardingValue<>(entry.getKey().getTableName(), entry.getKey().getName(), rangeValue));
            } else {
                if (null != rangeValue) {
                    try {
                        listValue = getOptimizeValue(listValue, rangeValue);
                    } catch (ClassCastException e) {
                        throw new ShardingJdbcException("Found different types for sharding value `%s`.", entry.getKey());
                    }
                }
                if (null == listValue) {
                    return new AlwaysFalseShardingCondition();
                }
                result.add(new ListShardingValue<>(entry.getKey().getTableName(), entry.getKey().getName(), listValue));
            }
        }
        return result;
    }
    
    private Map<Column, List<Condition>> getConditionsMap(final AndCondition andCondition, final GeneratedKey generatedKey) {
        Map<Column, List<Condition>> result = new LinkedHashMap<>(andCondition.getConditions().size() + 1, 1);
        for (Condition each : andCondition.getConditions()) {
            if (!result.containsKey(each.getColumn())) {
                result.put(each.getColumn(), new LinkedList<Condition>());
            }
            result.get(each.getColumn()).add(each);
        }
        if (null != generatedKey) {
            result.put(generatedKey.getColumn(), Arrays.<Condition>asList(new GeneratedKeyCondition(generatedKey)));
        }
        return result;
    }
    
    private List<Comparable<?>> getValues(final Condition condition, final List<?> parameters) {
        List<Comparable<?>> result = new LinkedList<>(condition.getPositionValueMap().values());
        for (Entry<Integer, Integer> entry : condition.getPositionIndexMap().entrySet()) {
            Object parameter = parameters.get(entry.getValue());
            if (!(parameter instanceof Comparable<?>)) {
                throw new ShardingJdbcException("Parameter `%s` should extends Comparable for sharding value.", parameter);
            }
            if (entry.getKey() < result.size()) {
                result.add(entry.getKey(), (Comparable<?>) parameter);
            } else {
                result.add((Comparable<?>) parameter);
            }
        }
        return result;
    }
    
    private List<Comparable<?>> getOptimizeValue(final List<Comparable<?>> listValue1, final List<Comparable<?>> listValue2) {
        if (null == listValue1) {
            return listValue2;
        }
        if (null == listValue2) {
            return listValue1;
        }
        listValue1.retainAll(listValue2);
        if (listValue1.isEmpty()) {
            return null;
        }
        return listValue1;
    }
    
    private Range<Comparable<?>> getOptimizeValue(final Range<Comparable<?>> rangeValue1, final Range<Comparable<?>> rangeValue2) {
        if (null == rangeValue1) {
            return rangeValue2;
        }
        if (null == rangeValue2) {
            return rangeValue1;
        }
        return rangeValue1.intersection(rangeValue2);
    }
    
    private List<Comparable<?>> getOptimizeValue(final List<Comparable<?>> listValue, final Range<Comparable<?>> rangeValue) {
        List<Comparable<?>> result = new LinkedList<>();
        for (Comparable<?> each : listValue) {
            if (rangeValue.contains(each)) {
                result.add(each);
            }
        }
        if (result.isEmpty()) {
            return null;
        }
        return result;
    }
    
}