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

package org.projectforge.web.fibu;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.form.validation.IFormValidator;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.convert.IConverter;
import org.projectforge.calendar.DayHolder;
import org.projectforge.common.NumberHelper;
import org.projectforge.common.StringHelper;
import org.projectforge.core.Configuration;
import org.projectforge.core.ConfigurationParam;
import org.projectforge.core.CurrencyFormatter;
import org.projectforge.fibu.AbstractRechnungDO;
import org.projectforge.fibu.AbstractRechnungsPositionDO;
import org.projectforge.fibu.AuftragsPositionDO;
import org.projectforge.fibu.RechnungsPositionDO;
import org.projectforge.fibu.kost.KostZuweisungDO;
import org.projectforge.fibu.kost.KostZuweisungenCopyHelper;
import org.projectforge.web.dialog.ModalDialog;
import org.projectforge.web.wicket.AbstractEditForm;
import org.projectforge.web.wicket.AbstractEditPage;
import org.projectforge.web.wicket.bootstrap.GridBuilder;
import org.projectforge.web.wicket.bootstrap.GridSize;
import org.projectforge.web.wicket.components.DatePanel;
import org.projectforge.web.wicket.components.DatePanelSettings;
import org.projectforge.web.wicket.components.LabelValueChoiceRenderer;
import org.projectforge.web.wicket.components.MaxLengthTextArea;
import org.projectforge.web.wicket.components.MinMaxNumberField;
import org.projectforge.web.wicket.components.SingleButtonPanel;
import org.projectforge.web.wicket.converter.BigDecimalPercentConverter;
import org.projectforge.web.wicket.converter.CurrencyConverter;
import org.projectforge.web.wicket.flowlayout.ButtonPanel;
import org.projectforge.web.wicket.flowlayout.ButtonType;
import org.projectforge.web.wicket.flowlayout.DivPanel;
import org.projectforge.web.wicket.flowlayout.DivTextPanel;
import org.projectforge.web.wicket.flowlayout.FieldSetIconPosition;
import org.projectforge.web.wicket.flowlayout.FieldsetPanel;
import org.projectforge.web.wicket.flowlayout.IconPanel;
import org.projectforge.web.wicket.flowlayout.IconType;
import org.projectforge.web.wicket.flowlayout.InputPanel;
import org.projectforge.web.wicket.flowlayout.TextAreaPanel;
import org.projectforge.web.wicket.flowlayout.TextPanel;
import org.projectforge.web.wicket.flowlayout.TextStyle;
import org.projectforge.web.wicket.flowlayout.ToggleContainerPanel;
import org.projectforge.web.wicket.flowlayout.ToggleContainerPanel.ToggleStatus;

public abstract class AbstractRechnungEditForm<O extends AbstractRechnungDO<T>, T extends AbstractRechnungsPositionDO, P extends AbstractEditPage< ? , ? , ? >>
extends AbstractEditForm<O, P>
{
  private static final long serialVersionUID = 9073611406229693582L;

  public static final int[] ZAHLUNGSZIELE_IN_TAGEN = { 7, 14, 30, 60, 90};

  private static final Component[] COMPONENT_ARRAY = new Component[0];

  protected RepeatingView positionsRepeater;

  private boolean costConfigured;

  private CostEditModalDialog costEditModalDialog;

  private final List<Component> ajaxUpdateComponents = new ArrayList<Component>();

  private Component[] ajaxUpdateComponentsArray;

  protected final FormComponent< ? >[] dependentFormComponents = new FormComponent[5];

  protected DatePanel datumPanel, faelligkeitPanel;

  protected Integer zahlungsZiel;

  public AbstractRechnungEditForm(final P parentPage, final O data)
  {
    super(parentPage, data);
  }

  protected abstract void onInit();

  @SuppressWarnings("unchecked")
  protected void validation()
  {
    final TextField<Date> datumField = (TextField<Date>) dependentFormComponents[0];
    final TextField<Date> bezahlDatumField = (TextField<Date>) dependentFormComponents[1];
    final TextField<Date> faelligkeitField = (TextField<Date>) dependentFormComponents[2];
    final TextField<BigDecimal> zahlBetragField = (TextField<BigDecimal>) dependentFormComponents[3];
    final DropDownChoice<Integer> zahlungsZielChoice = (DropDownChoice<Integer>) dependentFormComponents[4];

    final Date bezahlDatum = bezahlDatumField.getConvertedInput();

    final Integer zahlungsZiel = zahlungsZielChoice.getConvertedInput();
    Date faelligkeit = faelligkeitField.getConvertedInput();
    if (faelligkeit == null && zahlungsZiel != null) {
      Date date = datumField.getConvertedInput();
      if (date == null) {
        date = getData().getDatum();
      }
      if (date != null) {
        final DayHolder day = new DayHolder(date);
        day.add(Calendar.DAY_OF_YEAR, zahlungsZiel);
        faelligkeit = day.getDate();
        getData().setFaelligkeit(day.getSQLDate());
        faelligkeitPanel.markModelAsChanged();
      }
    }
    getData().recalculate();

    final BigDecimal zahlBetrag = zahlBetragField.getConvertedInput();
    final boolean zahlBetragExists = (zahlBetrag != null && zahlBetrag.compareTo(BigDecimal.ZERO) != 0);
    if (bezahlDatum != null && zahlBetragExists == false) {
      addError("fibu.rechnung.error.bezahlDatumUndZahlbetragRequired");
    }
    if (faelligkeit == null) {
      addFieldRequiredError("fibu.rechnung.faelligkeit");
    }
  }

  @SuppressWarnings("serial")
  @Override
  protected void init()
  {
    super.init();
    add(new IFormValidator() {
      @Override
      public FormComponent< ? >[] getDependentFormComponents()
      {
        return dependentFormComponents;
      }

      @Override
      public void validate(final Form< ? > form)
      {
        validation();
      }
    });
    if (Configuration.getInstance().isCostConfigured() == true) {
      costConfigured = true;
    }
    addCloneButton();

    onInit();

    // GRID 50% - BLOCK
    gridBuilder.newSplitPanel(GridSize.COL50, true);
    gridBuilder.newSubSplitPanel(GridSize.COL50);
    {
      // Date
      final FieldsetPanel fs = gridBuilder.newFieldset(AbstractRechnungDO.class, "datum");
      datumPanel = new DatePanel(fs.newChildId(), new PropertyModel<Date>(data, "datum"), DatePanelSettings.get().withTargetType(
          java.sql.Date.class));
      dependentFormComponents[0] = datumPanel.getDateField();
      datumPanel.setRequired(true);
      fs.add(datumPanel);
    }
    gridBuilder.newSubSplitPanel(GridSize.COL50);
    {
      // Net sum
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.common.netto"));
      final DivTextPanel netPanel = new DivTextPanel(fs.newChildId(), new Model<String>() {
        @Override
        public String getObject()
        {
          return CurrencyFormatter.format(data.getNetSum());
        }
      }, TextStyle.FORM_TEXT);
      fs.add(netPanel);
      fs.suppressLabelForWarning();
      ajaxUpdateComponents.add(netPanel.getLabel4Ajax());
    }
    gridBuilder.newSubSplitPanel(GridSize.COL50);
    {
      // Vat amount
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.common.vatAmount"));
      final DivTextPanel vatPanel = new DivTextPanel(fs.newChildId(), new Model<String>() {
        @Override
        public String getObject()
        {
          return CurrencyFormatter.format(data.getVatAmountSum());
        }
      }, TextStyle.FORM_TEXT);
      fs.add(vatPanel);
      fs.suppressLabelForWarning();
      ajaxUpdateComponents.add(vatPanel.getLabel4Ajax());
    }
    gridBuilder.newSubSplitPanel(GridSize.COL50);
    {
      // Brutto
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.common.brutto"));
      final DivTextPanel grossPanel = new DivTextPanel(fs.newChildId(), new Model<String>() {
        @Override
        public String getObject()
        {
          return CurrencyFormatter.format(data.getGrossSum());
        }
      }, TextStyle.FORM_TEXT);
      fs.add(grossPanel);
      fs.suppressLabelForWarning();
      ajaxUpdateComponents.add(grossPanel.getLabel4Ajax());
    }
    gridBuilder.newSubSplitPanel(GridSize.COL50);
    {
      // Bezahldatum
      final FieldsetPanel fs = gridBuilder.newFieldset(AbstractRechnungDO.class, "bezahlDatum");
      final DatePanel bezahlDatumPanel = new DatePanel(fs.newChildId(), new PropertyModel<Date>(data, "bezahlDatum"), DatePanelSettings
          .get().withTargetType(java.sql.Date.class));
      dependentFormComponents[1] = bezahlDatumPanel.getDateField();
      fs.add(bezahlDatumPanel);
    }
    gridBuilder.newSubSplitPanel(GridSize.COL50);
    {
      // Zahlbetrag
      final FieldsetPanel fs = gridBuilder.newFieldset(AbstractRechnungDO.class, "zahlBetrag");
      final TextField<BigDecimal> zahlBetragField = new TextField<BigDecimal>(InputPanel.WICKET_ID, new PropertyModel<BigDecimal>(data,
          "zahlBetrag")) {
        @SuppressWarnings({ "rawtypes", "unchecked"})
        @Override
        public IConverter getConverter(final Class type)
        {
          return new CurrencyConverter();
        }
      };
      dependentFormComponents[3] = zahlBetragField;
      fs.add(zahlBetragField);
    }
    {
      gridBuilder.newSubSplitPanel(GridSize.COL50);
      // Fälligkeit und Zahlungsziel
      final FieldsetPanel fs = gridBuilder.newFieldset(AbstractRechnungDO.class, "faelligkeit");
      faelligkeitPanel = new DatePanel(fs.newChildId(), new PropertyModel<Date>(data, "faelligkeit"), DatePanelSettings.get()
          .withTargetType(java.sql.Date.class));
      dependentFormComponents[2] = faelligkeitPanel.getDateField();
      fs.add(faelligkeitPanel);
      fs.setLabelFor(faelligkeitPanel);
      addCellAfterFaelligkeit();

      // DropDownChoice ZahlungsZiel
      final LabelValueChoiceRenderer<Integer> zielChoiceRenderer = new LabelValueChoiceRenderer<Integer>();
      for (final int days : ZAHLUNGSZIELE_IN_TAGEN) {
        zielChoiceRenderer.addValue(days, String.valueOf(days) + " " + getString("days"));
      }
      final DropDownChoice<Integer> zahlungsZielChoice = new DropDownChoice<Integer>(fs.getDropDownChoiceId(), new PropertyModel<Integer>(
          this, "zahlungsZiel"), zielChoiceRenderer.getValues(), zielChoiceRenderer) {
        @Override
        public boolean isVisible()
        {
          return data.getFaelligkeit() == null;
        }
      };
      dependentFormComponents[4] = zahlungsZielChoice;
      zahlungsZielChoice.setNullValid(true);
      zahlungsZielChoice.setRequired(false);

      fs.add(zahlungsZielChoice);
      fs.add(new DivTextPanel(fs.newChildId(), new Model<String>() {
        @Override
        public String getObject()
        {
          data.recalculate();
          return data.getZahlungsZielInTagen() + " " + getString("days");
        }
      }) {
        @Override
        public boolean isVisible()
        {
          return data.getFaelligkeit() != null;
        }
      });
    }
    // GRID 50% - BLOCK
    gridBuilder.newSplitPanel(GridSize.COL50);
    {
      // Bemerkung
      final FieldsetPanel fs = gridBuilder.newFieldset(AbstractRechnungDO.class, "bemerkung");
      fs.add(new MaxLengthTextArea(TextAreaPanel.WICKET_ID, new PropertyModel<String>(data, "bemerkung")), true);
    }
    gridBuilder.newSplitPanel(GridSize.COL50);
    {
      // Besonderheiten
      final FieldsetPanel fs = gridBuilder.newFieldset(AbstractRechnungDO.class, "besonderheiten");
      fs.add(new MaxLengthTextArea(TextAreaPanel.WICKET_ID, new PropertyModel<String>(data, "besonderheiten")), true);
    }
    gridBuilder.newGridPanel();
    positionsRepeater = gridBuilder.newRepeatingView();
    if (costConfigured == true) {
      addCostEditModalDialog();
    }
    refresh();
    if (getBaseDao().hasInsertAccess(getUser()) == true) {
      final DivPanel panel = gridBuilder.newGridPanel().getPanel();
      final Button addPositionButton = new Button(SingleButtonPanel.WICKET_ID) {
        @Override
        public final void onSubmit()
        {
          final T position = newPositionInstance();
          data.addPosition(position);
          if (position.getNumber() > 1) {
            final T predecessor = data.getPosition(position.getNumber() - 2);
            if (predecessor != null) {
              position.setVat(predecessor.getVat()); // Preset the vat from the predecessor position.
            }
          }
          refresh();
        }
      };
      final SingleButtonPanel addPositionButtonPanel = new SingleButtonPanel(panel.newChildId(), addPositionButton, getString("add"));
      addPositionButtonPanel.setTooltip(getString("fibu.rechnung.tooltip.addPosition"));
      panel.add(addPositionButtonPanel);
    }
  }

  protected void addCellAfterFaelligkeit()
  {
    // Do nothing.
  }

  protected abstract T newPositionInstance();

  @SuppressWarnings("serial")
  void refresh()
  {
    positionsRepeater.removeAll();
    final boolean hasInsertAccess = getBaseDao().hasInsertAccess(getUser());
    if (CollectionUtils.isEmpty(data.getPositionen()) == true) {
      // Ensure that at least one position is available:
      final T position = newPositionInstance();
      position.setVat(Configuration.getInstance().getPercentValue(ConfigurationParam.FIBU_DEFAULT_VAT));
      data.addPosition(position);
    }
    for (final T position : data.getPositionen()) {
      // Fetch all kostZuweisungen:
      if (CollectionUtils.isNotEmpty(position.getKostZuweisungen()) == true) {
        for (final KostZuweisungDO zuweisung : position.getKostZuweisungen()) {
          zuweisung.getNetto(); // Fetch
        }
      }
      final List<Component> ajaxUpdatePositionComponents = new ArrayList<Component>();
      final RechnungsPositionDO rechnungsPosition = (position instanceof RechnungsPositionDO) ? (RechnungsPositionDO) position : null;
      final ToggleContainerPanel positionsPanel = new ToggleContainerPanel(positionsRepeater.newChildId()) {
        /**
         * @see org.projectforge.web.wicket.flowlayout.ToggleContainerPanel#wantsOnStatusChangedNotification()
         */
        @Override
        protected boolean wantsOnStatusChangedNotification()
        {
          return true;
        }

        /**
         */
        @Override
        protected void onToggleStatusChanged(final AjaxRequestTarget target, final ToggleStatus toggleStatus)
        {
          if (toggleStatus == ToggleStatus.OPENED) {
            data.getUiStatus().openPosition(position.getNumber());
          } else {
            data.getUiStatus().closePosition(position.getNumber());
          }
          setHeading(getPositionHeading(position, this));
        }
      };
      positionsPanel.getContainer().setOutputMarkupId(true);
      positionsRepeater.add(positionsPanel);
      if (data.getUiStatus().isClosed(position.getNumber()) == true) {
        positionsPanel.setClosed();
      } else {
        positionsPanel.setOpen();
      }
      positionsPanel.setHeading(getPositionHeading(position, positionsPanel));
      final GridBuilder posGridBuilder = positionsPanel.createGridBuilder();
      final GridSize gridSize = (rechnungsPosition != null) ? GridSize.COL25 : GridSize.COL33;
      {
        posGridBuilder.newSplitPanel(GridSize.COL50, true);
        if (rechnungsPosition != null) {
          // Order
          posGridBuilder.newSubSplitPanel(gridSize); // COL25
          final FieldsetPanel fieldset = posGridBuilder.newFieldset(getString("fibu.auftrag")).setLabelSide(false);
          fieldset.add(new InputPanel(fieldset.newChildId(), new AuftragsPositionFormComponent(InputPanel.WICKET_ID,
              new PropertyModel<AuftragsPositionDO>(position, "auftragsPosition"), false)));
          fieldset.add(new IconPanel(fieldset.newIconChildId(), IconType.GOTO, getString("show")) {
            /**
             * @see org.apache.wicket.markup.html.link.Link#onClick()
             */
            @Override
            public void onClick()
            {
              if (rechnungsPosition.getAuftragsPosition() != null) {
                final PageParameters parameters = new PageParameters();
                parameters.add(AbstractEditPage.PARAMETER_KEY_ID, rechnungsPosition.getAuftragsPosition().getAuftrag().getId());
                final AuftragEditPage auftragEditPage = new AuftragEditPage(parameters);
                auftragEditPage.setReturnToPage(getParentPage());
                setResponsePage(auftragEditPage);
              }
            }

            @Override
            public boolean isVisible()
            {
              return rechnungsPosition.getAuftragsPosition() != null;
            }
          }.enableAjaxOnClick(), FieldSetIconPosition.TOP_RIGHT);
        }
        {
          // Menge
          posGridBuilder.newSubSplitPanel(gridSize);
          final FieldsetPanel fieldset = posGridBuilder.newFieldset(getString("fibu.rechnung.menge")).setLabelSide(false);
          final TextField<BigDecimal> amountTextField = new MinMaxNumberField<BigDecimal>(InputPanel.WICKET_ID,
              new PropertyModel<BigDecimal>(position, "menge"), BigDecimal.ZERO, NumberHelper.BILLION);
          amountTextField.add(new AjaxFormComponentUpdatingBehavior("onblur") {
            @Override
            protected void onUpdate(final AjaxRequestTarget target)
            {
              addAjaxComponents(target, ajaxUpdatePositionComponents);
            }
          });
          fieldset.add(amountTextField);
        }
        {
          // Net price
          posGridBuilder.newSubSplitPanel(gridSize);
          final FieldsetPanel fieldset = posGridBuilder.newFieldset(getString("fibu.rechnung.position.einzelNetto")).setLabelSide(false);
          final TextField<BigDecimal> netTextField = new TextField<BigDecimal>(InputPanel.WICKET_ID, new PropertyModel<BigDecimal>(
              position, "einzelNetto")) {
            @SuppressWarnings({ "rawtypes", "unchecked"})
            @Override
            public IConverter getConverter(final Class type)
            {
              return new CurrencyConverter();
            }
          };
          netTextField.add(new AjaxFormComponentUpdatingBehavior("onblur") {
            @Override
            protected void onUpdate(final AjaxRequestTarget target)
            {
              addAjaxComponents(target, ajaxUpdatePositionComponents);
            }
          });
          fieldset.add(netTextField);
        }
        {
          // VAT
          posGridBuilder.newSubSplitPanel(gridSize);
          final FieldsetPanel fieldset = posGridBuilder.newFieldset(getString("fibu.rechnung.mehrwertSteuerSatz")).setLabelSide(false);
          final TextField<BigDecimal> vatTextField = new MinMaxNumberField<BigDecimal>(InputPanel.WICKET_ID, new PropertyModel<BigDecimal>(
              position, "vat"), BigDecimal.ZERO, NumberHelper.HUNDRED) {
            @SuppressWarnings({ "rawtypes", "unchecked"})
            @Override
            public IConverter getConverter(final Class type)
            {
              return new BigDecimalPercentConverter(true);
            }
          };
          vatTextField.add(new AjaxFormComponentUpdatingBehavior("onblur") {
            @Override
            protected void onUpdate(final AjaxRequestTarget target)
            {
              addAjaxComponents(target, ajaxUpdatePositionComponents);
            }
          });
          fieldset.add(vatTextField);
        }
      }
      {
        posGridBuilder.newSplitPanel(GridSize.COL50, true);
        posGridBuilder.newSubSplitPanel(GridSize.COL33);
        {
          final FieldsetPanel fieldset = posGridBuilder.newFieldset(getString("fibu.common.netto")).setLabelSide(false)
              .suppressLabelForWarning();
          final TextPanel netTextPanel = new TextPanel(fieldset.newChildId(), new Model<String>() {
            @Override
            public String getObject()
            {
              return CurrencyFormatter.format(position.getNetSum());
            };
          });
          ajaxUpdatePositionComponents.add(netTextPanel.getLabel4Ajax());
          fieldset.add(netTextPanel);
        }
      }
      {
        posGridBuilder.newSubSplitPanel(GridSize.COL33);
        {
          final FieldsetPanel fieldset = posGridBuilder.newFieldset(getString("fibu.common.vatAmount")).setLabelSide(false)
              .suppressLabelForWarning();
          final TextPanel vatTextPanel = new TextPanel(fieldset.newChildId(), new Model<String>() {
            @Override
            public String getObject()
            {
              return CurrencyFormatter.format(position.getVatAmount());
            };
          });
          fieldset.add(vatTextPanel);
          ajaxUpdatePositionComponents.add(vatTextPanel.getLabel4Ajax());
        }
      }
      {
        posGridBuilder.newSubSplitPanel(GridSize.COL33);
        {
          final FieldsetPanel fieldset = posGridBuilder.newFieldset(getString("fibu.common.brutto")).setLabelSide(false)
              .suppressLabelForWarning();
          final TextPanel grossTextPanel = new TextPanel(fieldset.newChildId(), new Model<String>() {
            @Override
            public String getObject()
            {
              return CurrencyFormatter.format(position.getBruttoSum());
            };
          });
          fieldset.add(grossTextPanel);
          ajaxUpdatePositionComponents.add(grossTextPanel.getLabel4Ajax());
        }
      }
      {
        // Text
        if (costConfigured == true) {
          posGridBuilder.newSplitPanel(GridSize.COL50);
        } else {
          posGridBuilder.newGridPanel();
        }
        final FieldsetPanel fieldset = posGridBuilder.newFieldset(getString("fibu.rechnung.text"));
        fieldset.add(new MaxLengthTextArea(TextAreaPanel.WICKET_ID, new PropertyModel<String>(position, "text")), true);
      }
      if (costConfigured == true) {
        {
          // Cost assignments
          posGridBuilder.newSplitPanel(GridSize.COL50, true);
          {
            posGridBuilder.newSubSplitPanel(GridSize.COL50);
            DivPanel panel = posGridBuilder.getPanel();
            final RechnungCostTablePanel costTable = new RechnungCostTablePanel(panel.newChildId(), position);
            panel.add(costTable);
            ajaxUpdatePositionComponents.add(costTable.refresh().getTable());

            posGridBuilder.newSubSplitPanel(GridSize.COL50);
            panel = posGridBuilder.getPanel();
            final BigDecimal fehlbetrag = position.getKostZuweisungNetFehlbetrag();
            if (hasInsertAccess == true) {
              ButtonType buttonType;
              if (NumberHelper.isNotZero(fehlbetrag) == true) {
                buttonType = ButtonType.RED;
              } else {
                buttonType = ButtonType.LIGHT;
              }
              final AjaxButton editCostButton = new AjaxButton(ButtonPanel.BUTTON_ID, this) {
                @Override
                protected void onSubmit(final AjaxRequestTarget target, final Form< ? > form)
                {
                  costEditModalDialog.open(target);
                  // Redraw the content:
                  costEditModalDialog.redraw(position, costTable);
                  // The content was changed:
                  costEditModalDialog.addContent(target);
                }

                @Override
                protected void onError(final AjaxRequestTarget target, final Form< ? > form)
                {
                  target.add(AbstractRechnungEditForm.this.feedbackPanel);
                }
              };
              editCostButton.setDefaultFormProcessing(false);
              panel.add(new ButtonPanel(panel.newChildId(), getString("edit"), editCostButton, buttonType));
            } else {
              panel.add(new TextPanel(panel.newChildId(), " "));
            }
            if (NumberHelper.isNotZero(fehlbetrag) == true) {
              panel.add(new TextPanel(panel.newChildId(), CurrencyFormatter.format(fehlbetrag), TextStyle.RED));
            }
          }
        }
      }
    }
  }

  protected String getPositionHeading(final AbstractRechnungsPositionDO position, final ToggleContainerPanel positionsPanel)
  {
    if (positionsPanel.getToggleStatus() == ToggleStatus.OPENED) {
      return getString("label.position.short") + " #" + position.getNumber();
    }
    final StringBuffer heading = new StringBuffer();
    heading.append(escapeHtml(getString("label.position.short"))).append(" #").append(position.getNumber());
    heading.append(": ").append(CurrencyFormatter.format(position.getNetSum()));
    if (StringHelper.isNotBlank(position.getText()) == true) {
      heading.append(" ").append(StringUtils.abbreviate(position.getText(), 80));
    }
    return heading.toString();
  }

  /**
   * Overwrite this method if you need to add own form elements for a order position.
   * @param item
   * @param position
   */
  protected void onRenderPosition(final WebMarkupContainer item, final T position)
  {

  }

  protected void addCostEditModalDialog()
  {
    costEditModalDialog = new CostEditModalDialog();
    final String title = (isNew() == true) ? "create" : "update";
    costEditModalDialog.setCloseButtonLabel(getString(title)).wantsNotificationOnClose().setOutputMarkupId(true);
    parentPage.add(costEditModalDialog);
    costEditModalDialog.init();
  }

  private class CostEditModalDialog extends ModalDialog
  {
    private static final long serialVersionUID = 7113006438653862995L;

    private RechnungCostEditTablePanel rechnungCostEditTablePanel;

    private AbstractRechnungsPositionDO position;

    private RechnungCostTablePanel costTable;

    CostEditModalDialog()
    {
      super(parentPage.newModalDialogId());
      setBigWindow().setEscapeKeyEnabled(false);
    }

    @Override
    public void init()
    {
      setTitle(getString("fibu.rechnung.showEditableKostZuweisungen"));
      init(new Form<String>(getFormId()));
    }

    public void redraw(final AbstractRechnungsPositionDO position, final RechnungCostTablePanel costTable)
    {
      this.position = position;
      this.costTable = costTable;
      clearContent();
      {
        final DivPanel panel = gridBuilder.getPanel();
        rechnungCostEditTablePanel = new RechnungCostEditTablePanel(panel.newChildId());
        panel.add(rechnungCostEditTablePanel);
        rechnungCostEditTablePanel.add(position);
      }
    }

    /**
     * @see org.projectforge.web.dialog.ModalDialog#handleCloseEvent(org.apache.wicket.ajax.AjaxRequestTarget)
     */
    @Override
    protected void handleCloseEvent(final AjaxRequestTarget target)
    {
      // Copy edited values to DO object.
      final AbstractRechnungsPositionDO srcPosition = rechnungCostEditTablePanel.getPosition();
      final KostZuweisungenCopyHelper kostZuweisungCopyHelper = new KostZuweisungenCopyHelper();
      kostZuweisungCopyHelper.mycopy(srcPosition.getKostZuweisungen(), position.getKostZuweisungen(), position);
      target.add(costTable.refresh().getTable());
    }
  }

  /**
   * @return null
   */
  public Long getBezahlDatumInMillis()
  {
    return null;
  }

  /**
   * Dummy method. Does nothing.
   * @param bezahlDatumInMillis
   */
  public void setBezahlDatumInMillis(final Long bezahlDatumInMillis)
  {
  }

  private void addAjaxComponents(final AjaxRequestTarget target, final List<Component> components)
  {
    target.add(components.toArray(COMPONENT_ARRAY));
    if (ajaxUpdateComponentsArray == null) {
      ajaxUpdateComponentsArray = ajaxUpdateComponents.toArray(COMPONENT_ARRAY);
    }
    target.add(ajaxUpdateComponentsArray);
  }
}
