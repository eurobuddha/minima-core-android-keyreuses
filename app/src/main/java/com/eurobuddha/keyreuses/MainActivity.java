package com.eurobuddha.keyreuses;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.text.HtmlCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * KeyUses — audits this node's 64 keys for one-time-signature (Winternitz) re-use.
 *
 * <p>Native port of the KeyUses MiniDapp ({@code mds/keyuses/minidapp/index.html} v0.1.51). One
 * screen, one node command ({@code keys action:list}), addresses derived on-device, and two
 * parallel HTTPS lookups against the hosted archive index.
 *
 * <p>The dapp is a single vertically-scrolling column, so the whole UI is built programmatically
 * into the layout's container — the fleet idiom.
 */
public class MainActivity extends AppCompatActivity {

    private static final String NODE_PACKAGE = "org.minimarex.minimacore";
    private static final String VERSION = "0.1.0";

    private NodeApi node;
    private AuditApi api;

    private LinearLayout container;
    private LinearLayout pairingBanner;
    private LinearLayout runButton;
    private TextView runLabel;
    private ProgressBar runSpinner;
    private TextView statusBox;
    private LinearLayout resultsBox;

    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // This screen shows the node's full public-key and address set — keep it out of the
        // recents thumbnail and away from screenshots, matching the family standard.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_main);
        container = findViewById(R.id.container);

        final View scroll = findViewById(R.id.scroll);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            final Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        node = new NodeApi(this, enabled -> setPaired(enabled));
        api = new AuditApi(this);

        buildUi();

        // A miscompiled SHA3 would derive wrong addresses, and a wrong address looks like "never
        // spent" — i.e. it would report SAFE. Refuse to run rather than reassure wrongly.
        if (!Sha3.selfTest()) {
            runButton.setEnabled(false);
            runButton.setAlpha(0.5f);
            showError("Internal error: the SHA3-256 self-test failed, so addresses cannot be "
                    + "derived safely. This build is broken — do not trust any result from it.");
        }
    }

    @Override
    protected void onDestroy() {
        if (node != null) node.onDestroy();
        if (api != null) api.onDestroy();
        super.onDestroy();
    }

    /* ====================== the audit ====================== */

    private void runAudit() {
        if (busy) return;
        clearError();
        setBusy(true);
        resultsBox.removeAllViews();
        resultsBox.setVisibility(View.GONE);

        node.cmd("keys action:list", new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {
                setPaired(true);
                final JSONObject resp = json.optJSONObject("response");
                final JSONArray keys = resp == null ? null : resp.optJSONArray("keys");
                if (!json.optBoolean("status", true) || keys == null) {
                    setBusy(false);
                    showError(Copy.ERR_KEYS);
                    return;
                }
                onKeys(keys);
            }

            @Override public void onError(String message) {
                setBusy(false);
                if (NodeApi.ERR_NOT_ENABLED.equals(message)) {
                    setPaired(false);
                } else {
                    showError(message);
                }
            }
        });
    }

    private void onKeys(JSONArray keys) {
        final List<KeyAudit.LocalKey> local = new ArrayList<>();
        final List<MinimaAddress.Result> derived = new ArrayList<>();
        final List<String> pubs = new ArrayList<>();
        final List<String> addrs = new ArrayList<>();

        for (int i = 0; i < keys.length(); i++) {
            final JSONObject k = keys.optJSONObject(i);
            if (k == null) continue;
            final String pk = k.optString("publickey", "");
            local.add(new KeyAudit.LocalKey(pk, k.optInt("uses", 0), k.optInt("maxuses", 0)));

            final MinimaAddress.Result d = MinimaAddress.fromPublicKey(pk);
            derived.add(d);
            if (!pk.isEmpty()) pubs.add(pk);
            if (d != null) addrs.add(d.hex);
        }

        if (local.isEmpty()) {
            setBusy(false);
            showError(Copy.ERR_KEYS);
            return;
        }

        api.audit(pubs, addrs, new AuditApi.Cb() {
            @Override
            public void onResult(List<KeyAudit.Usage> usage, List<KeyAudit.Reuse> reuse, long tip) {
                setBusy(false);
                renderResults(KeyAudit.join(local, usage, reuse, derived), tip);
            }

            @Override public void onError(String message) {
                setBusy(false);
                showError(message);
            }
        });
    }

    /* ====================== rendering ====================== */

    private void renderResults(KeyAudit.Result r, long archiveTip) {
        resultsBox.removeAllViews();

        if (r.derivationMismatch) {
            resultsBox.addView(noticeCard(Copy.MISMATCH, Design.RISK));
        }
        if (r.exhaustion) {
            resultsBox.addView(noticeCard(Copy.EXHAUSTION, Design.WARN));
        }

        resultsBox.addView(verdictCard(r));
        resultsBox.addView(tableCard(r, archiveTip));
        resultsBox.setVisibility(View.VISIBLE);
    }

    private View verdictCard(KeyAudit.Result r) {
        final int color;
        final String title, body;
        switch (r.verdict) {
            case REUSE_RISK:
                color = Design.RISK;
                title = Copy.REUSE_RISK_TITLE;
                body = Copy.reuseRiskBody(num(r.worstReuse));
                break;
            case REUSE_WARN:
                color = Design.WARN;
                title = Copy.REUSE_WARN_TITLE;
                body = Copy.reuseWarnBody(num(r.worstReuse));
                break;
            case RISK_HEURISTIC:
                color = Design.RISK;
                title = Copy.RISK_TITLE;
                body = Copy.RISK_BODY;
                break;
            default:
                color = Design.OK;
                title = Copy.OK_TITLE;
                body = Copy.OK_BODY;
                break;
        }

        final int soft = color == Design.OK ? Design.OK_SOFT
                : color == Design.WARN ? Design.WARN_SOFT : Design.RISK_SOFT;

        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Design.card(this, soft, Design.alpha(color, 0x59), 14));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setLayoutParams(marginParams(0, 0, 0, 14));

        final TextView h = text(title, 19f, color, Typeface.BOLD);
        h.setGravity(Gravity.CENTER);
        card.addView(h);

        final TextView p = html(body, 13.5f, Design.TEXT);
        p.setGravity(Gravity.CENTER);
        p.setPadding(0, dp(8), 0, 0);
        p.setLineSpacing(dp(3), 1f);
        card.addView(p);

        // Confirmed re-use gets the "why this happened" list; the advice there is migrate, not
        // resync better, so it deliberately does NOT show the keyuses recommendation
        // (index.html:288, :297).
        if (r.verdict == KeyAudit.Verdict.REUSE_RISK || r.verdict == KeyAudit.Verdict.REUSE_WARN) {
            card.addView(insetBlock(Copy.WHY_LABEL, Copy.WHY_LIST, false));
        } else if (r.verdict == KeyAudit.Verdict.RISK_HEURISTIC) {
            card.addView(insetBlock(Copy.HOW_LABEL, Copy.HOW_LIST, false));
            card.addView(insetBlock(Copy.DO_LABEL, Copy.DO_LIST, false));
            card.addView(recoBlock(r.recommendedKeyUses));
        } else {
            card.addView(recoBlock(r.recommendedKeyUses));
        }

        return card;
    }

    /** The .reco box: the exact command to run on the next seed re-sync, tap to copy. */
    private View recoBlock(long keyuses) {
        final String cmd = Copy.recoCommand(keyuses);
        final LinearLayout box = (LinearLayout) insetBlock(Copy.RECO_LABEL, null, true);
        final TextView code = text(cmd, 12f, Design.ACCENT, Typeface.NORMAL);
        code.setTypeface(Typeface.MONOSPACE);
        code.setLineSpacing(dp(3), 1f);
        box.addView(code);
        box.setOnClickListener(v -> copy(cmd, "Command copied"));
        return box;
    }

    private View insetBlock(String label, String bodyHtml, boolean empty) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Design.card(this, Design.INSET, Design.BORDER, 10));
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setLayoutParams(marginParams(0, 14, 0, 0));

        final TextView l = text(label, 10.5f, Design.DIM, Typeface.BOLD);
        l.setLetterSpacing(0.08f);
        box.addView(l);

        if (!empty && bodyHtml != null) {
            final TextView b = html(bodyHtml, 12.5f, Design.TEXT);
            b.setPadding(0, dp(8), 0, 0);
            b.setLineSpacing(dp(4), 1f);
            box.addView(b);
        }
        return box;
    }

    private View noticeCard(String body, int color) {
        final int soft = color == Design.WARN ? Design.WARN_SOFT : Design.RISK_SOFT;
        final TextView t = html(body, 12.5f, color);
        t.setBackground(Design.card(this, soft, Design.alpha(color, 0x59), 10));
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        t.setLineSpacing(dp(3), 1f);
        t.setLayoutParams(marginParams(0, 0, 0, 12));
        return t;
    }

    /* ---------- results table ---------- */

    // Fixed column widths so the header and rows line up. Total exceeds a phone's width, so the
    // table scrolls horizontally — the same behaviour as the dapp's `.tablewrap { overflow-x: auto }`.
    private static final int COL_ADDR = 190, COL_USES = 66, COL_SIGS = 82, COL_COINS = 66, COL_STATUS = 90;

    private View tableCard(KeyAudit.Result r, long archiveTip) {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Design.card(this, Design.CARD, Design.BORDER, 16));
        card.setPadding(dp(12), dp(16), dp(12), dp(16));
        card.setLayoutParams(marginParams(0, 0, 0, 14));

        final LinearLayout table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);

        table.addView(headerRow());
        for (KeyAudit.Row row : r.rows) table.addView(row(row));

        final HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(table);
        card.addView(hs);

        final TextView cov = text(Copy.coverage(archiveTip), 10.5f, Design.DIM, Typeface.NORMAL);
        cov.setPadding(dp(2), dp(14), dp(2), 0);
        cov.setLineSpacing(dp(3), 1f);
        card.addView(cov);

        return card;
    }

    private View headerRow() {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(8));

        row.addView(headerCell("Address (tap to copy)", COL_ADDR, Gravity.START));
        row.addView(headerCell("Node uses", COL_USES, Gravity.END));
        row.addView(headerCell("On-chain signatures", COL_SIGS, Gravity.END));
        row.addView(headerCell("Spent coins", COL_COINS, Gravity.END));
        row.addView(headerCell("Status", COL_STATUS, Gravity.END));

        final LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row);
        wrap.addView(divider());
        return wrap;
    }

    private TextView headerCell(String s, int widthDp, int gravity) {
        final TextView t = text(s, 9.5f, Design.DIM, Typeface.BOLD);
        t.setLetterSpacing(0.06f);
        t.setAllCaps(true);
        t.setGravity(gravity);
        t.setWidth(dp(widthDp));
        t.setPadding(dp(4), 0, dp(4), 0);
        return t;
    }

    private View row(final KeyAudit.Row r) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(9), 0, dp(9));

        // address cell: "#N" then the Mx address, wrapping inside its column
        final LinearLayout addrCell = new LinearLayout(this);
        addrCell.setOrientation(LinearLayout.VERTICAL);
        addrCell.setLayoutParams(new LinearLayout.LayoutParams(dp(COL_ADDR),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addrCell.setPadding(dp(4), 0, dp(4), 0);

        final TextView n = text("#" + r.index, 9.5f, Design.DIM_2, Typeface.NORMAL);
        addrCell.addView(n);

        final TextView a = text(r.address, 9.5f, Design.DIM, Typeface.NORMAL);
        a.setTypeface(Typeface.MONOSPACE);
        a.setLineSpacing(dp(1), 1f);
        addrCell.addView(a);

        addrCell.setOnClickListener(v -> {
            copy(r.address, "Address copied");
            a.setTextColor(Design.ACCENT);
            a.postDelayed(() -> a.setTextColor(Design.DIM), 1300);
        });
        row.addView(addrCell);

        row.addView(numCell(num(r.local), COL_USES));
        row.addView(numCell(num(r.sigs), COL_SIGS));
        row.addView(numCell(num(r.coins), COL_COINS));

        // status chip
        final String label;
        final int fg, bg;
        switch (r.status()) {
            case REUSED:
                final boolean red = r.reuseCount > KeyAudit.REUSE_RED_ABOVE;
                fg = red ? Design.RISK : Design.WARN;
                bg = red ? Design.RISK_SOFT : Design.WARN_SOFT;
                label = "RE-USED" + (r.reuseCount > 0 ? " ×" + r.reuseCount : "");
                break;
            case AT_RISK:
                fg = Design.RISK;
                bg = Design.RISK_SOFT;
                label = "AT RISK";
                break;
            default:
                fg = Design.OK;
                bg = Design.OK_SOFT;
                label = "OK";
                break;
        }

        final TextView chip = text(label, 9.5f, fg, Typeface.BOLD);
        chip.setBackground(Design.chip(this, bg));
        chip.setPadding(dp(8), dp(3), dp(8), dp(3));
        chip.setGravity(Gravity.CENTER);

        final LinearLayout chipCell = new LinearLayout(this);
        chipCell.setLayoutParams(new LinearLayout.LayoutParams(dp(COL_STATUS),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        chipCell.setGravity(Gravity.END);
        chipCell.setPadding(dp(4), 0, dp(4), 0);
        chipCell.addView(chip);
        row.addView(chipCell);

        final LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row);
        wrap.addView(divider());
        return wrap;
    }

    private TextView numCell(String s, int widthDp) {
        final TextView t = text(s, 12f, Design.TEXT, Typeface.NORMAL);
        t.setGravity(Gravity.END);
        t.setWidth(dp(widthDp));
        t.setPadding(dp(4), 0, dp(4), 0);
        return t;
    }

    private View divider() {
        final View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f))));
        v.setBackgroundColor(Design.BORDER);
        return v;
    }

    /* ====================== static chrome ====================== */

    private void buildUi() {
        container.addView(badge());
        container.addView(title());
        container.addView(subtitle());
        container.addView(pairingBanner = pairingBanner());
        container.addView(controlsCard());

        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        resultsBox.setVisibility(View.GONE);
        container.addView(resultsBox);

        container.addView(explainerCard());
        container.addView(footer());
    }

    private View badge() {
        final LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setBackground(Design.card(this, Design.ACCENT_SOFT, Design.ACCENT_SOFT, 4));
        b.setPadding(dp(12), dp(5), dp(12), dp(5));

        final View dot = new View(this);
        final LinearLayout.LayoutParams dp6 = new LinearLayout.LayoutParams(dp(6), dp(6));
        dp6.rightMargin = dp(8);
        dot.setLayoutParams(dp6);
        dot.setBackground(Design.dot(Design.ACCENT));
        b.addView(dot);

        final TextView t = text(Copy.BADGE, 10f, Design.ACCENT, Typeface.BOLD);
        t.setLetterSpacing(0.1f);
        b.addView(t);

        final LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.addView(b);
        wrap.setLayoutParams(marginParams(0, 0, 0, 16));
        return wrap;
    }

    private View title() {
        final TextView t = text(Copy.TITLE, 26f, Design.TEXT, Typeface.BOLD);
        t.setLetterSpacing(-0.02f);
        t.setLayoutParams(marginParams(0, 0, 0, 8));
        return t;
    }

    private View subtitle() {
        final TextView t = html(Copy.SUBTITLE, 13.5f, Design.DIM);
        t.setLineSpacing(dp(4), 1f);
        t.setLayoutParams(marginParams(0, 0, 0, 20));
        return t;
    }

    private LinearLayout pairingBanner() {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Design.card(this, Design.ACCENT_SOFT, Design.alpha(Design.ACCENT, 0x66), 14));
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setLayoutParams(marginParams(0, 0, 0, 14));
        card.setVisibility(View.GONE);

        card.addView(text(Copy.ERR_PAIRING_TITLE, 14f, Design.ACCENT, Typeface.BOLD));

        final TextView body = text(Copy.ERR_PAIRING_BODY, 12.5f, Design.TEXT, Typeface.NORMAL);
        body.setPadding(0, dp(6), 0, dp(12));
        body.setLineSpacing(dp(3), 1f);
        card.addView(body);

        final TextView btn = text("Open Minima Core", 13f, 0xFFFFFFFF, Typeface.BOLD);
        btn.setBackground(Design.accentButton(this, 10));
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(14), dp(11), dp(14), dp(11));
        btn.setOnClickListener(v -> openNode());
        card.addView(btn);

        return card;
    }

    private View controlsCard() {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Design.card(this, Design.CARD, Design.BORDER, 16));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setLayoutParams(marginParams(0, 0, 0, 14));

        runButton = new LinearLayout(this);
        runButton.setOrientation(LinearLayout.HORIZONTAL);
        runButton.setGravity(Gravity.CENTER);
        runButton.setBackground(Design.accentButton(this, 10));
        runButton.setPadding(dp(14), dp(14), dp(14), dp(14));
        runButton.setOnClickListener(v -> runAudit());

        runSpinner = new ProgressBar(this);
        final LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(16), dp(16));
        sp.rightMargin = dp(10);
        runSpinner.setLayoutParams(sp);
        runSpinner.setIndeterminate(true);
        runSpinner.setVisibility(View.GONE);
        runButton.addView(runSpinner);

        runLabel = text("Run Audit", 14.5f, 0xFFFFFFFF, Typeface.BOLD);
        runButton.addView(runLabel);
        card.addView(runButton);

        final TextView privacy = html(Copy.PRIVACY, 11f, Design.DIM);
        privacy.setPadding(0, dp(14), 0, 0);
        privacy.setLineSpacing(dp(3), 1f);
        card.addView(privacy);

        statusBox = html("", 12f, Design.RISK);
        statusBox.setBackground(Design.card(this, Design.RISK_SOFT, Design.alpha(Design.RISK, 0x40), 10));
        statusBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        statusBox.setLineSpacing(dp(3), 1f);
        statusBox.setVisibility(View.GONE);
        statusBox.setLayoutParams(marginParams(0, 14, 0, 0));
        card.addView(statusBox);

        return card;
    }

    private View explainerCard() {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Design.card(this, Design.CARD, Design.BORDER, 16));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setLayoutParams(marginParams(0, 0, 0, 14));

        final TextView label = text(Copy.EXPLAIN_LABEL, 10.5f, Design.DIM, Typeface.BOLD);
        label.setLetterSpacing(0.08f);
        card.addView(label);

        final TextView body = html(Copy.EXPLAIN_BODY, 12.5f, Design.TEXT);
        body.setPadding(0, dp(10), 0, 0);
        body.setLineSpacing(dp(4), 1f);
        card.addView(body);

        return card;
    }

    private View footer() {
        final TextView t = text("eurobuddha · Minima Ecosystem · KeyUses v" + VERSION,
                10f, Design.DIM_2, Typeface.NORMAL);
        t.setGravity(Gravity.CENTER);
        t.setLetterSpacing(0.05f);
        t.setPadding(0, dp(6), 0, dp(10));
        return t;
    }

    /* ====================== state ====================== */

    private void setBusy(boolean b) {
        busy = b;
        runButton.setEnabled(!b);
        runButton.setAlpha(b ? 0.5f : 1f);
        runSpinner.setVisibility(b ? View.VISIBLE : View.GONE);
        runLabel.setText(b ? "Auditing…" : "Run Audit");
    }

    private void setPaired(boolean enabled) {
        if (pairingBanner != null) {
            pairingBanner.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
    }

    private void showError(String msg) {
        statusBox.setText(HtmlCompat.fromHtml(msg, HtmlCompat.FROM_HTML_MODE_COMPACT));
        statusBox.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        statusBox.setVisibility(View.GONE);
    }

    private void openNode() {
        final Intent i = getPackageManager().getLaunchIntentForPackage(NODE_PACKAGE);
        if (i != null) {
            startActivity(i);
        } else {
            Toast.makeText(this, "Minima Core is not installed", Toast.LENGTH_LONG).show();
        }
    }

    private void copy(String s, String toast) {
        final ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("keyuses", s));
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }

    /* ====================== view helpers ====================== */

    private TextView text(String s, float sizeSp, int color, int style) {
        final TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        if (style != Typeface.NORMAL) t.setTypeface(Typeface.DEFAULT, style);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private TextView html(String s, float sizeSp, int color) {
        final TextView t = new TextView(this);
        t.setText(HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_COMPACT));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout.LayoutParams marginParams(int l, int t, int r, int b) {
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(float v) {
        return Design.dpi(this, v);
    }

    private static String num(long v) {
        return NumberFormat.getInstance(Locale.getDefault()).format(v);
    }
}
