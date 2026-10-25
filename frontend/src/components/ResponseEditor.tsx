interface ResponseEditorProps {
  id: string;
  value: string;
  disabled: boolean;
  onChange: (value: string) => void;
}

export function ResponseEditor({
  id,
  value,
  disabled,
  onChange,
}: ResponseEditorProps) {
  return (
    <div className="details-section">
      <label className="response-editor-label" htmlFor={id}>
        Suggested response to resident
        <span>Edit before approving if needed</span>
      </label>

      <textarea
        id={id}
        className="response-editor"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        rows={7}
        disabled={disabled}
      />
    </div>
  );
}
