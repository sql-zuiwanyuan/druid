/*
 * Druid - a distributed column store.
 * Copyright (C) 2012, 2013  Metamarkets Group Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package io.druid.storage.mysql;

import com.google.common.base.Supplier;
import com.google.inject.Inject;
import com.metamx.common.logger.Logger;
import io.druid.db.MetadataStorageConnectorConfig;
import io.druid.db.MetadataStorageTablesConfig;
import io.druid.db.SQLMetadataConnector;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.HandleCallback;

import java.util.List;
import java.util.Map;

public class MySQLConnector extends SQLMetadataConnector
{
  private static final Logger log = new Logger(MySQLConnector.class);
  private static final String PAYLOAD_TYPE = "LONGBLOB";
  private static final String SERIAL_TYPE = "BIGINT(20) AUTO_INCREMENT";

  private final DBI dbi;

  @Inject
  public MySQLConnector(Supplier<MetadataStorageConnectorConfig> config, Supplier<MetadataStorageTablesConfig> dbTables)
  {
    super(config, dbTables);
    this.dbi = new DBI(getDatasource());
    dbi.withHandle(new HandleCallback<Void>()
                   {
                     @Override
                     public Void withHandle(Handle handle) throws Exception
                     {
                       handle.createStatement("SET sql_mode='ANSI_QUOTES'").execute();
                       return null;
                     }
                   });
  }

  @Override
  protected String getPayloadType()
  {
    return PAYLOAD_TYPE;
  }

  @Override
  protected String getSerialType()
  {
    return SERIAL_TYPE;
  }

  @Override
  protected boolean tableExists(Handle handle, String tableName)
  {
    return !handle.createQuery("SHOW tables LIKE :tableName")
                  .bind("tableName", tableName)
                  .list()
                  .isEmpty();
  }

  @Override
  public Void insertOrUpdate(
      final String tableName,
      final String keyColumn,
      final String valueColumn,
      final String key,
      final byte[] value
  ) throws Exception
  {
    return getDBI().withHandle(
        new HandleCallback<Void>()
        {
          @Override
          public Void withHandle(Handle handle) throws Exception
          {
            handle.createStatement(
                String.format(
                    "INSERT INTO %1$s (%2$s, %3$s) VALUES (:key, :value) ON DUPLICATE KEY UPDATE %3$s = :value",
                    tableName,
                    keyColumn,
                    valueColumn
                )
            )
                  .bind("key", key)
                  .bind("value", value)
                  .execute();
            return null;
          }
        }
    );
  }

  @Override
  public DBI getDBI() { return dbi; }
}
