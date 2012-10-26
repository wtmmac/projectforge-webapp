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

import java.util.ArrayList;
import java.util.List;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.ModificationItem;

import org.apache.commons.collections.CollectionUtils;

/**
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
public class LdapGroupDao extends LdapDao<String, LdapGroup>
{
  private static final String[] ADDITIONAL_OBJECT_CLASSES = null;// { "posixGroup"};// null;//{ "groupOfNames"};

  private static final String NONE_UNIQUE_MEMBER_ID = "cn=none";

  /**
   * Since member of groups can't be null, "cn=none" if the group has no real members.
   * @param group
   * @return
   */
  public static boolean hasMembers(final LdapGroup group)
  {
    if (group.getMembers() == null || group.getMembers().size() == 0) {
      return false;
    }
    if (group.getMembers().size() > 1) {
      return true;
    }
    return group.getMembers().iterator().next().startsWith(NONE_UNIQUE_MEMBER_ID) == false;
  }

  /**
   * @see org.projectforge.ldap.LdapDao#getObjectClass()
   */
  @Override
  protected String getObjectClass()
  {
    return "groupOfUniqueNames";
  }

  /**
   * @see org.projectforge.ldap.LdapDao#getAdditionalObjectClasses()
   */
  @Override
  protected String[] getAdditionalObjectClasses()
  {
    return ADDITIONAL_OBJECT_CLASSES;
  }

  /**
   * @see org.projectforge.ldap.LdapDao#getIdAttrId()
   */
  @Override
  public String getIdAttrId()
  {
    return "businessCategory";
  }

  /**
   * @see org.projectforge.ldap.LdapDao#getId(org.projectforge.ldap.LdapObject)
   */
  @Override
  public String getId(final LdapGroup obj)
  {
    return obj.getBusinessCategory();
  }

  /**
   * Used for bind and update.
   * @param person
   * @return
   * @see org.projectforge.ldap.LdapDao#getModificationItems(org.projectforge.ldap.LdapObject)
   */
  @Override
  protected ModificationItem[] getModificationItems(final LdapGroup group)
  {
    final List<ModificationItem> list = new ArrayList<ModificationItem>();
    createAndAddModificationItems(list, "businessCategory", group.getBusinessCategory());
    createAndAddModificationItems(list, "o", group.getOrganization());
    createAndAddModificationItems(list, "description", group.getDescription());
    if (CollectionUtils.isNotEmpty(group.getMembers()) == true) {
      createAndAddModificationItems(list, "uniqueMember", group.getMembers());
    } else {
      createAndAddModificationItems(list, "uniqueMember", NONE_UNIQUE_MEMBER_ID);
    }
    return list.toArray(new ModificationItem[list.size()]);
  }

  /**
   * @see org.projectforge.ldap.LdapDao#mapToObject(java.lang.String, javax.naming.directory.Attributes)
   */
  @Override
  protected LdapGroup mapToObject(final String dn, final Attributes attributes) throws NamingException
  {
    final LdapGroup group = new LdapGroup();
    group.setBusinessCategory(LdapUtils.getAttributeStringValue(attributes, "businessCategory"));
    group.setDescription(LdapUtils.getAttributeStringValue(attributes, "description"));
    group.setOrganization(LdapUtils.getAttributeStringValue(attributes, "o"));
    final String[] members = LdapUtils.getAttributeStringValues(attributes, "uniqueMember");
    if (members != null) {
      for (final String member : members) {
        group.addMember(member, ldapConfig.getBaseDN());
      }
    }
    return group;
  }

  /**
   * @see org.projectforge.ldap.LdapDao#buildId(java.lang.Object)
   */
  @Override
  protected String buildId(final Object id)
  {
    if (id == null) {
      return null;
    }
    if (id instanceof String && ((String) id).startsWith(GroupDOConverter.ID_PREFIX) == true) {
      return String.valueOf(id);
    }
    return GroupDOConverter.ID_PREFIX + id;
  }

  /**
   * @see org.projectforge.ldap.LdapDao#getOuBase()
   */
  @Override
  protected String getOuBase()
  {
    return ldapConfig.getGroupBase();
  }
}
