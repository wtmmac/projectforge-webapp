/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2013 Kai Reinhard (k.reinhard@micromata.de)
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

package org.projectforge.fibu;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.projectforge.access.AccessChecker;
import org.projectforge.common.DateFormatType;
import org.projectforge.common.DateFormats;
import org.projectforge.common.NumberHelper;
import org.projectforge.excel.ContentProvider;
import org.projectforge.excel.ExportColumn;
import org.projectforge.excel.ExportSheet;
import org.projectforge.excel.ExportWorkbook;
import org.projectforge.excel.I18nExportColumn;
import org.projectforge.excel.PropertyMapping;
import org.projectforge.export.MyXlsContentProvider;
import org.projectforge.registry.Registry;
import org.projectforge.task.TaskNode;
import org.projectforge.task.TaskTree;
import org.projectforge.user.PFUserContext;
import org.projectforge.user.PFUserDO;

/**
 * For excel export.
 * 
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
public class OrderExport
{
  private class MyContentProvider extends MyXlsContentProvider
  {
    public MyContentProvider(final ExportWorkbook workbook)
    {
      super(workbook);
    }

    @Override
    public org.projectforge.excel.ContentProvider newInstance()
    {
      return new MyContentProvider(this.workbook);
    }
  };

  private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(OrderExport.class);

  protected AccessChecker accessChecker;

  private AuftragDao auftragDao;

  private RechnungCache rechnungCache;

  private TaskTree taskTree;

  private enum OrderCol
  {
    NUMMER, NUMBER_OF_POSITIONS, DATE, ORDER_DATE, STATUS, PROJECT, PROJECT_CUSTOMER, TITLE, NETSUM, INVOICED, TO_BE_INVOICED, COMPLETELY_INVOICED, INVOICES, CONTACT_PERSON, REFERENCE, COMMENT;
  }

  private enum PosCol
  {
    NUMBER, POS_NUMBER, DATE, PROJECT, ORDER_TITLE, TITLE, TYPE, STATUS, PERSON_DAYS, NETSUM, INVOICED, TO_BE_INVOICED, COMPLETELY_INVOICED, INVOICES, PERIOD_OF_PERFORMANCE_BEGIN, PERIOD_OF_PERFORMANCE_END, TASK, COMMENT;
  }

  private ExportColumn[] createOrderColumns()
  {
    return new ExportColumn[] { //
        new I18nExportColumn(OrderCol.NUMMER, "fibu.auftrag.nummer.short", MyXlsContentProvider.LENGTH_ID),
        new I18nExportColumn(OrderCol.NUMBER_OF_POSITIONS, "fibu.auftrag.positions", MyXlsContentProvider.LENGTH_ID),
        new I18nExportColumn(OrderCol.DATE, "fibu.auftrag.datum", MyXlsContentProvider.LENGTH_DATE),
        new I18nExportColumn(OrderCol.ORDER_DATE, "fibu.auftrag.beauftragungsdatum", MyXlsContentProvider.LENGTH_DATE),
        new I18nExportColumn(OrderCol.STATUS, "status", 10),
        new I18nExportColumn(OrderCol.PROJECT, "fibu.projekt", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(OrderCol.PROJECT_CUSTOMER, "fibu.kunde", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(OrderCol.TITLE, "fibu.auftrag.titel", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(OrderCol.NETSUM, "fibu.auftrag.nettoSumme", MyXlsContentProvider.LENGTH_CURRENCY), //
        new I18nExportColumn(OrderCol.INVOICED, "fibu.fakturiert", MyXlsContentProvider.LENGTH_CURRENCY), //
        new I18nExportColumn(OrderCol.TO_BE_INVOICED, "fibu.tobeinvoiced", MyXlsContentProvider.LENGTH_CURRENCY),
        new I18nExportColumn(OrderCol.COMPLETELY_INVOICED, "fibu.auftrag.vollstaendigFakturiert", MyXlsContentProvider.LENGTH_BOOLEAN),
        new I18nExportColumn(OrderCol.INVOICES, "fibu.rechnungen", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(OrderCol.CONTACT_PERSON, "contactPerson", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(OrderCol.REFERENCE, "fibu.common.reference", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(OrderCol.COMMENT, "comment", MyXlsContentProvider.LENGTH_COMMENT)};
  }

  private void addOrderMapping(final PropertyMapping mapping, final AuftragDO order, final Object... params)
  {
    auftragDao.calculateInvoicedSum(order);
    mapping.add(OrderCol.NUMMER, order.getNummer());
    mapping.add(OrderCol.NUMBER_OF_POSITIONS, "#" + (order.getPositionen() != null ? order.getPositionen().size() : "0"));
    mapping.add(OrderCol.DATE, order.getAngebotsDatum());
    mapping.add(OrderCol.ORDER_DATE, order.getBeauftragungsDatum());
    mapping.add(OrderCol.STATUS,
        order.getAuftragsStatus() != null ? PFUserContext.getLocalizedString(order.getAuftragsStatus().getI18nKey()) : "");
    mapping.add(OrderCol.PROJECT, order.getProjektAsString());
    final ProjektDO project = order.getProjekt();
    final String projectCustomer = KundeFormatter.formatKundeAsString(project != null ? project.getKunde() : null, order.getKundeText());
    mapping.add(OrderCol.PROJECT_CUSTOMER, projectCustomer);
    mapping.add(OrderCol.TITLE, order.getTitel());
    final BigDecimal netSum = order.getNettoSumme() != null ? order.getNettoSumme() : BigDecimal.ZERO;
    final BigDecimal invoicedSum = order.getFakturiertSum() != null ? order.getFakturiertSum() : BigDecimal.ZERO;
    final BigDecimal toBeInvoicedSum = netSum.subtract(invoicedSum);
    mapping.add(OrderCol.NETSUM, netSum);
    addCurrency(mapping, OrderCol.INVOICED, invoicedSum);
    addCurrency(mapping, OrderCol.TO_BE_INVOICED, toBeInvoicedSum);
    mapping.add(OrderCol.COMPLETELY_INVOICED, order.isVollstaendigFakturiert() == true ? "x" : "");
    final Set<RechnungsPositionVO> invoicePositions = rechnungCache.getRechnungsPositionVOSetByAuftragId(order.getId());
    mapping.add(OrderCol.INVOICES, getInvoices(invoicePositions));
    final PFUserDO contactPerson = Registry.instance().getUserGroupCache().getUser(order.getContactPersonId());
    mapping.add(OrderCol.CONTACT_PERSON, contactPerson != null ? contactPerson.getFullname() : "");
    mapping.add(OrderCol.REFERENCE, order.getReferenz());
    mapping.add(OrderCol.COMMENT, order.getBemerkung());
  }

  private ExportColumn[] createPosColumns()
  {
    return new ExportColumn[] { //
        new I18nExportColumn(PosCol.NUMBER, "fibu.auftrag.nummer.short", MyXlsContentProvider.LENGTH_ID),
        new I18nExportColumn(PosCol.POS_NUMBER, "fibu.auftrag.position", 5),
        new I18nExportColumn(PosCol.DATE, "fibu.auftrag.datum", MyXlsContentProvider.LENGTH_DATE),
        new I18nExportColumn(PosCol.PROJECT, "fibu.projekt", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(PosCol.ORDER_TITLE, "fibu.auftrag.titel", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(PosCol.TITLE, "fibu.auftrag.titel", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(PosCol.TYPE, "fibu.auftrag.position.art", 10),
        new I18nExportColumn(PosCol.STATUS, "status", 10),
        new I18nExportColumn(PosCol.PERSON_DAYS, "projectmanagement.personDays.short", 8),
        new I18nExportColumn(PosCol.NETSUM, "fibu.auftrag.nettoSumme", MyXlsContentProvider.LENGTH_CURRENCY), //
        new I18nExportColumn(PosCol.INVOICED, "fibu.fakturiert", MyXlsContentProvider.LENGTH_CURRENCY), //
        new I18nExportColumn(PosCol.TO_BE_INVOICED, "fibu.tobeinvoiced", MyXlsContentProvider.LENGTH_CURRENCY),
        new I18nExportColumn(PosCol.COMPLETELY_INVOICED, "fibu.auftrag.vollstaendigFakturiert", MyXlsContentProvider.LENGTH_BOOLEAN),
        new I18nExportColumn(PosCol.INVOICES, "fibu.rechnungen", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(PosCol.PERIOD_OF_PERFORMANCE_BEGIN, null, MyXlsContentProvider.LENGTH_DATE),
        new I18nExportColumn(PosCol.PERIOD_OF_PERFORMANCE_END, null, MyXlsContentProvider.LENGTH_DATE),
        new I18nExportColumn(PosCol.TASK, "task", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(PosCol.COMMENT, "comment", MyXlsContentProvider.LENGTH_COMMENT)};
  }

  private void addPosMapping(final PropertyMapping mapping, final AuftragDO order, final AuftragsPositionDO pos, final Object... params)
  {
    mapping.add(PosCol.NUMBER, order.getNummer());
    mapping.add(PosCol.POS_NUMBER, "#" + pos.getNumber());
    mapping.add(PosCol.DATE, order.getAngebotsDatum());
    mapping.add(PosCol.PROJECT, order.getProjektAsString());
    mapping.add(PosCol.ORDER_TITLE, order.getTitel());
    mapping.add(PosCol.TITLE, pos.getTitel());
    mapping.add(PosCol.TYPE, pos.getArt() != null ? PFUserContext.getLocalizedString(pos.getArt().getI18nKey()) : "");
    mapping.add(PosCol.STATUS, pos.getStatus() != null ? PFUserContext.getLocalizedString(pos.getStatus().getI18nKey()) : "");
    mapping.add(PosCol.PERSON_DAYS, pos.getPersonDays());
    final BigDecimal netSum = pos.getNettoSumme() != null ? pos.getNettoSumme() : BigDecimal.ZERO;
    final BigDecimal invoicedSum = pos.getFakturiertSum() != null ? pos.getFakturiertSum() : BigDecimal.ZERO;
    final BigDecimal toBeInvoicedSum = netSum.subtract(invoicedSum);
    mapping.add(PosCol.NETSUM, netSum);
    addCurrency(mapping, PosCol.INVOICED, invoicedSum);
    addCurrency(mapping, PosCol.TO_BE_INVOICED, toBeInvoicedSum);
    mapping.add(PosCol.COMPLETELY_INVOICED, pos.isVollstaendigFakturiert() == true ? "x" : "");
    final Set<RechnungsPositionVO> invoicePositions = rechnungCache.getRechnungsPositionVOSetByAuftragsPositionId(pos.getId());
    mapping.add(PosCol.INVOICES, getInvoices(invoicePositions));
    mapping.add(PosCol.PERIOD_OF_PERFORMANCE_BEGIN, pos.getPeriodOfPerformanceBegin());
    mapping.add(PosCol.PERIOD_OF_PERFORMANCE_END, pos.getPeriodOfPerformanceEnd());
    final TaskNode node = taskTree.getTaskNodeById(pos.getTaskId());
    mapping.add(PosCol.TASK, node != null ? node.getTask().getTitle() : "");
    mapping.add(PosCol.COMMENT, pos.getBemerkung());
  }

  private String getInvoices(final Set<RechnungsPositionVO> invoicePositions)
  {
    final StringBuilder sb = new StringBuilder();
    if (invoicePositions != null) {
      String delimiter = "";
      for (final RechnungsPositionVO invoicePos : invoicePositions) {
        sb.append(delimiter).append(invoicePos.getRechnungNummer());
        delimiter = ", ";
      }
    }
    return sb.toString();
  }

  private void addCurrency(final PropertyMapping mapping, final Enum< ? > col, final BigDecimal value)
  {
    if (NumberHelper.isNotZero(value) == true) {
      mapping.add(col, value);
    } else {
      mapping.add(col, "");
    }
  }

  /**
   * Exports the filtered list as table with almost all fields. For members of group FINANCE_GROUP (PF_Finance) and MARKETING_GROUP
   * (PF_Marketing) all addresses are exported, for others only those which are marked as personal favorites.
   * @param Used by sub classes such as AddressCampaignValueExport.
   * @throws IOException
   */
  public byte[] export(final List<AuftragDO> list, final Object... params)
  {
    if (CollectionUtils.isEmpty(list) == true) {
      return null;
    }
    log.info("Exporting order list.");
    final ExportWorkbook xls = new ExportWorkbook();
    final ContentProvider contentProvider = new MyContentProvider(xls);
    // create a default Date format and currency column
    xls.setContentProvider(contentProvider);

    ExportColumn[] columns = createOrderColumns();
    String sheetTitle = PFUserContext.getLocalizedString("fibu.auftrag.auftraege");
    ExportSheet sheet = xls.addSheet(sheetTitle);
    ContentProvider sheetProvider = sheet.getContentProvider();
    sheetProvider.putFormat(MyXlsContentProvider.FORMAT_CURRENCY, OrderCol.NETSUM, OrderCol.INVOICED, OrderCol.TO_BE_INVOICED);
    sheetProvider.putFormat(DateFormats.getExcelFormatString(DateFormatType.DATE), OrderCol.DATE, OrderCol.ORDER_DATE);
    sheet.createFreezePane(1, 1);
    sheet.setColumns(columns);
    for (final AuftragDO order : list) {
      final PropertyMapping mapping = new PropertyMapping();
      addOrderMapping(mapping, order, params);
      sheet.addRow(mapping.getMapping(), 0);
    }
    sheet.setAutoFilter();
    columns = createPosColumns();
    sheetTitle = PFUserContext.getLocalizedString("fibu.auftrag.positions");
    sheet = xls.addSheet(sheetTitle);
    sheetProvider = sheet.getContentProvider();
    sheetProvider.putFormat(MyXlsContentProvider.FORMAT_CURRENCY, PosCol.NETSUM, PosCol.INVOICED, PosCol.TO_BE_INVOICED);
    sheetProvider.putFormat(DateFormats.getExcelFormatString(DateFormatType.DATE), PosCol.DATE, PosCol.PERIOD_OF_PERFORMANCE_BEGIN,
        PosCol.PERIOD_OF_PERFORMANCE_END);
    sheet.createFreezePane(1, 1);
    sheet.setColumns(columns);
    sheet.setMergedRegion(0, 0, PosCol.PERIOD_OF_PERFORMANCE_BEGIN.ordinal(), PosCol.PERIOD_OF_PERFORMANCE_END.ordinal(),
        PFUserContext.getLocalizedString("fibu.periodOfPerformance"));
    for (final AuftragDO order : list) {
      if (order.getPositionen() == null) {
        continue;
      }
      for (final AuftragsPositionDO pos : order.getPositionen()) {
        final PropertyMapping mapping = new PropertyMapping();
        addPosMapping(mapping, order, pos, params);
        sheet.addRow(mapping.getMapping(), 0);
      }
    }
    sheet.setAutoFilter();
    return xls.getAsByteArray();
  }

  public void setAccessChecker(final AccessChecker accessChecker)
  {
    this.accessChecker = accessChecker;
  }

  public void setAuftragDao(final AuftragDao auftragDao)
  {
    this.auftragDao = auftragDao;
  }

  public void setRechnungCache(final RechnungCache rechnungCache)
  {
    this.rechnungCache = rechnungCache;
  }

  public void setTaskTree(final TaskTree taskTree)
  {
    this.taskTree = taskTree;
  }
}
