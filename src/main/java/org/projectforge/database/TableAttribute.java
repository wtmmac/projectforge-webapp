/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2011 Kai Reinhard (k.reinhard@me.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.database;

import javax.persistence.Column;
import javax.persistence.Id;

import org.apache.commons.lang.StringUtils;
import org.projectforge.common.BeanHelper;

import com.ibm.icu.math.BigDecimal;

/**
 * Represents one attribute of a table (e. g. for creation).
 * 
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
public class TableAttribute
{
  private boolean nullable = true;

  private TableAttributeType type;

  private String name;

  private int length = 255;

  private int precision = 0;

  private int scale = 0;

  private boolean primaryKey;

  private boolean generated;

  private boolean unique;

  private String foreignTable;

  private String foreignAttribute;

  private String defaultValue;

  public TableAttribute(final Class< ? > clazz, final String property)
  {
    this.name = property;
    final Class< ? > dType = BeanHelper.determinePropertyType(BeanHelper.determineGetter(clazz, property));
    if (Boolean.class.isAssignableFrom(dType) == true) {
      type = TableAttributeType.BOOLEAN;
    } else if (String.class.isAssignableFrom(dType) == true) {
      type = TableAttributeType.VARCHAR;
    } else if (Integer.class.isAssignableFrom(dType) == true) {
      type = TableAttributeType.INT;
    } else if (BigDecimal.class.isAssignableFrom(dType) == true) {
      type = TableAttributeType.DECIMAL;
    } else if (java.sql.Date.class.isAssignableFrom(dType) == true) {
      type = TableAttributeType.DATE;
    } else if (java.util.Date.class.isAssignableFrom(dType) == true) {
      type = TableAttributeType.TIMESTAMP;
    }
    final Id id = DatabaseUpdateDao.getIdAnnotation(clazz, property);
    if (id != null) {
      this.primaryKey = true;
      this.nullable = false;
    }
    final Column column = DatabaseUpdateDao.getColumnAnnotation(clazz, property);
    if (column != null) {
      if (isPrimaryKey() == false) {
        this.nullable = column.nullable();
      }
      if (StringUtils.isNotEmpty(column.name()) == true) {
        this.name = column.name();
      }
      if (type.isIn(TableAttributeType.VARCHAR, TableAttributeType.CHAR) == true) {
        this.length = column.length();
      }
      if (type == TableAttributeType.DECIMAL) {
        this.precision = column.precision();
        this.scale = column.scale();
      }
      this.unique = column.unique();
    }
  }

  public TableAttribute(final String name, final TableAttributeType type)
  {
    this.name = name;
    this.type = type;
  }

  public TableAttribute(final String name, final TableAttributeType type, final boolean nullable)
  {
    this(name, type);
    this.nullable = nullable;
  }

  public TableAttribute(final String name, final TableAttributeType type, final int length)
  {
    this(name, type);
    if (type != TableAttributeType.VARCHAR && type != TableAttributeType.CHAR) {
      throw new UnsupportedOperationException("Length not supported for attributes of type '" + type + "'.");
    }
    this.length = length;
  }

  public TableAttribute(final String name, final TableAttributeType type, final int length, final boolean nullable)
  {
    this(name, type, length);
    this.nullable = nullable;
  }

  public TableAttribute(final String name, final TableAttributeType type, final int precision, final int scale)
  {
    this(name, type);
    if (type != TableAttributeType.DECIMAL) {
      throw new UnsupportedOperationException("Precision and scale not supported for attributes of type '" + type + "'.");
    }
    this.precision = precision;
    this.scale = scale;
  }

  public TableAttribute(final String name, final TableAttributeType type, final int precision, final int scale, final boolean nullable)
  {
    this(name, type, precision, scale);
    this.nullable = nullable;
  }

  public boolean isNullable()
  {
    return nullable;
  }

  public TableAttribute setNullable(boolean nullable)
  {
    this.nullable = nullable;
    return this;
  }

  /**
   * Not yet supported.
   */
  public boolean isUnique()
  {
    return unique;
  }

  public TableAttributeType getType()
  {
    return type;
  }

  public TableAttribute setType(TableAttributeType type)
  {
    this.type = type;
    return this;
  }

  public String getName()
  {
    return name;
  }

  public TableAttribute setName(String name)
  {
    this.name = name;
    return this;
  }

  /**
   * Length of CHAR and VARCHAR.
   * @return
   */
  public int getLength()
  {
    return length;
  }

  public TableAttribute setLength(int length)
  {
    this.length = length;
    return this;
  }

  /**
   * Precision of numerical (decimal) values.
   */
  public int getPrecision()
  {
    return precision;
  }

  public TableAttribute setPrecision(int precision)
  {
    this.precision = precision;
    return this;
  }

  /**
   * Scale of numerical (decimal) values.
   */
  public int getScale()
  {
    return scale;
  }

  public TableAttribute setScale(int scale)
  {
    this.scale = scale;
    return this;
  }

  public boolean isPrimaryKey()
  {
    return primaryKey;
  }

  /**
   * Sets also this attribute as generated at default if it's from type INT.
   * @param primaryKey
   * @return
   */
  public TableAttribute setPrimaryKey(boolean primaryKey)
  {
    this.primaryKey = primaryKey;
    if (this.type == TableAttributeType.INT) {
      this.generated = true;
    }
    return this;
  }

  /**
   * True (default for primary keys of type INT) if the primary key should be generated by the database.
   */
  public boolean isGenerated()
  {
    return generated;
  }

  public TableAttribute setGenerated(boolean generated)
  {
    this.generated = generated;
    return this;
  }

  public String getForeignTable()
  {
    return foreignTable;
  }

  public TableAttribute setForeignTable(String foreignTable)
  {
    this.foreignTable = foreignTable;
    return this;
  }

  public String getForeignAttribute()
  {
    return foreignAttribute;
  }

  public TableAttribute setForeignAttribute(String foreignAttribute)
  {
    this.foreignAttribute = foreignAttribute;
    return this;
  }

  /**
   * @since 3.3.46 (didn't work before).
   */
  public String getDefaultValue()
  {
    return defaultValue;
  }

  public TableAttribute setDefaultValue(String defaultValue)
  {
    this.defaultValue = defaultValue;
    return this;
  }
}
