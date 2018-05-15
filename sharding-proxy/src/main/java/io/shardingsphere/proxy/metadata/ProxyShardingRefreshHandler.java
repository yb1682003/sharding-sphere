/*
 * Copyright 2016-2018 shardingsphere.io.
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

package io.shardingsphere.proxy.metadata;

import io.shardingsphere.core.metadata.AbstractRefreshHandler;
import io.shardingsphere.core.metadata.ShardingMetaData;
import io.shardingsphere.core.routing.SQLRouteResult;
import io.shardingsphere.core.rule.ShardingRule;
import io.shardingsphere.core.rule.TableRule;
import io.shardingsphere.proxy.config.RuleRegistry;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;

/**
 * Refresh table metadata of ProxySharding.
 *
 * @author zhaojun
 */
@Slf4j
public final class ProxyShardingRefreshHandler extends AbstractRefreshHandler {
    private ProxyShardingRefreshHandler(final SQLRouteResult routeResult, final ShardingMetaData shardingMetaData, final ShardingRule shardingRule) {
        super(routeResult, shardingMetaData, shardingRule);
    }

    @Override
    public void execute() {
        if (getRouteResult().canRefreshMetaData()) {
            String logicTable = getRouteResult().getSqlStatement().getTables().getSingleTableName();
            TableRule tableRule = getShardingRule().getTableRule(logicTable);
            try {
                getShardingMetaData().refresh(tableRule, getShardingRule());
            } catch (SQLException ex) {
                log.error("Refresh Sharding metadata error", ex);
            }
        }
    }

    /**
     * create new instance of {@code ProxyShardingRefreshHandler}.
     *
     * @param routeResult route result
     * @return {@code ProxyShardingRefreshHandler}
     */
    public static ProxyShardingRefreshHandler build(final SQLRouteResult routeResult) {
        return new ProxyShardingRefreshHandler(routeResult, RuleRegistry.getInstance().getShardingMetaData(), RuleRegistry.getInstance().getShardingRule());
    }
}