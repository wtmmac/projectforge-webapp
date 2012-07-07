/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2012 Kai Reinhard (k.reinhard@micromata.com)
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

package org.projectforge.ldap;

/**
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
public abstract class LdapObject
{
  private String dn, commonName;

  private String[] organizationalUnit;

  /**
   * @return the cn
   */
  public String getCommonName()
  {
    return commonName;
  }

  /**
   * cn
   * @param commonName the cn to set
   * @return this for chaining.
   */
  public LdapObject setCommonName(final String commonName)
  {
    this.commonName = commonName;
    return this;
  }

  /**
   * @return the dn
   */
  public String getDn()
  {
    return dn;
  }

  /**
   * @param dn the dn to set
   * @return this for chaining.
   */
  public void setDn(final String dn)
  {
    this.dn = dn;
  }

  /**
   * @return the organizationalUnit
   */
  public String[] getOrganizationalUnit()
  {
    return organizationalUnit;
  }

  /**
   * @param organizationalUnit the organizationalUnit to set
   * @return this for chaining.
   */
  public void setOrganizationalUnit(final String... organizationalUnit)
  {
    this.organizationalUnit = organizationalUnit;
  }
}
