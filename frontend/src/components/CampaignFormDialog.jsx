import { useEffect, useState } from "react";
import {
  CAMPAIGN_CHANNELS,
  CAMPAIGN_STATUSES,
  campaignToForm,
  emptyCampaignForm,
} from "../campaignForm";
import { formatStatus } from "../format";
import { IconClose } from "./Icons";

/** Modal form used for both creating and editing a marketing campaign. */
export default function CampaignFormDialog({
  campaign,
  isSaving,
  error,
  fieldErrors,
  onClose,
  onSubmit,
}) {
  const isEdit = Boolean(campaign);
  const [form, setForm] = useState(() =>
    isEdit ? campaignToForm(campaign) : emptyCampaignForm()
  );
  const [localErrors, setLocalErrors] = useState({});

  useEffect(() => {
    function onKeyDown(event) {
      if (event.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  function setField(name, value) {
    setForm((previous) => ({ ...previous, [name]: value }));
    setLocalErrors((previous) => ({ ...previous, [name]: undefined }));
  }

  function handleSubmit(event) {
    event.preventDefault();
    onSubmit(form);
  }

  function errorFor(name) {
    return localErrors[name] || (fieldErrors && fieldErrors[name]);
  }

  return (
    <div className="dash-modal-backdrop" role="presentation">
      <div
        className="dash-modal wide"
        role="dialog"
        aria-modal="true"
        aria-label={isEdit ? "Edit campaign" : "New campaign"}
      >
        <div className="dash-modal-head">
          <div>
            <span className="dash-eyebrow">Marketing</span>
            <h3>{isEdit ? "Edit campaign" : "New campaign"}</h3>
          </div>
          <button
            type="button"
            className="dash-icon-button"
            onClick={onClose}
            aria-label="Close"
          >
            <IconClose />
          </button>
        </div>

        <form className="dash-form" onSubmit={handleSubmit}>
          <div className="dash-form-grid">
            <label className="dash-field dash-field-wide">
              <span>Campaign name *</span>
              <input
                value={form.name}
                onChange={(event) => setField("name", event.target.value)}
                placeholder="Q4 Partner Webinar"
                maxLength={160}
                required
                disabled={isSaving}
              />
              {errorFor("name") && (
                <em className="dash-field-error">{errorFor("name")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Channel</span>
              <select
                value={form.channel}
                onChange={(event) => setField("channel", event.target.value)}
                disabled={isSaving}
              >
                {CAMPAIGN_CHANNELS.map((channel) => (
                  <option key={channel} value={channel}>
                    {formatStatus(channel)}
                  </option>
                ))}
              </select>
              {errorFor("channel") && (
                <em className="dash-field-error">{errorFor("channel")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Status</span>
              <select
                value={form.status}
                onChange={(event) => setField("status", event.target.value)}
                disabled={isSaving}
              >
                {CAMPAIGN_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {formatStatus(status)}
                  </option>
                ))}
              </select>
              {errorFor("status") && (
                <em className="dash-field-error">{errorFor("status")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Start date *</span>
              <input
                type="date"
                value={form.startDate}
                onChange={(event) => setField("startDate", event.target.value)}
                required
                disabled={isSaving}
              />
              {errorFor("startDate") && (
                <em className="dash-field-error">{errorFor("startDate")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>End date *</span>
              <input
                type="date"
                value={form.endDate}
                min={form.startDate || undefined}
                onChange={(event) => setField("endDate", event.target.value)}
                required
                disabled={isSaving}
              />
              {errorFor("endDate") && (
                <em className="dash-field-error">{errorFor("endDate")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Budget</span>
              <input
                type="number"
                min="0"
                step="0.01"
                value={form.budget}
                onChange={(event) => setField("budget", event.target.value)}
                placeholder="25000"
                disabled={isSaving}
              />
              {errorFor("budget") && (
                <em className="dash-field-error">{errorFor("budget")}</em>
              )}
            </label>

            <label className="dash-field">
              <span>Spend to date</span>
              <input
                type="number"
                min="0"
                step="0.01"
                value={form.spend}
                onChange={(event) => setField("spend", event.target.value)}
                placeholder="0"
                disabled={isSaving}
              />
              {errorFor("spend") && (
                <em className="dash-field-error">{errorFor("spend")}</em>
              )}
            </label>

            <label className="dash-field dash-field-wide">
              <span>Description</span>
              <textarea
                value={form.description}
                onChange={(event) =>
                  setField("description", event.target.value)
                }
                placeholder="Audience, offer, channels and goals…"
                rows={3}
                maxLength={2000}
                disabled={isSaving}
              />
              {errorFor("description") && (
                <em className="dash-field-error">
                  {errorFor("description")}
                </em>
              )}
            </label>
          </div>

          {error && (
            <p className="dash-form-error" role="alert">
              {error}
            </p>
          )}

          <div className="dash-form-actions">
            <button
              type="button"
              className="dash-button ghost"
              onClick={onClose}
              disabled={isSaving}
            >
              Cancel
            </button>
            <button type="submit" className="dash-button" disabled={isSaving}>
              {isSaving ? "Saving…" : isEdit ? "Save changes" : "Create campaign"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
