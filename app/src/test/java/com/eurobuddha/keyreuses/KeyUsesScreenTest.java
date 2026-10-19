package com.eurobuddha.keyreuses;

import android.view.*;
import android.widget.*;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Standalone adaptation of Pandamonium KeyUsesScreenTest's section/viewport checks. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class KeyUsesScreenTest {
    private View build(MainActivity activity,List<KeyAudit.Row> rows)throws Exception {
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat);
        KeyAudit.Result result=new KeyAudit.Result(rows,KeyAudit.Verdict.OK,0,256,false,false);
        Method m=MainActivity.class.getDeclaredMethod("tableCard",KeyAudit.Result.class,long.class);
        m.setAccessible(true);return (View)m.invoke(activity,result,123L);
    }
    static void noHorizontalScroll(View view) {
        assertFalse(view instanceof HorizontalScrollView);
        assertFalse("Public audit must have no phrase input",view instanceof EditText);
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)noHorizontalScroll(((ViewGroup)view).getChildAt(i));
    }
    static void assertChip(View root,int index,String label,int colour) {
        TextView chip=root.findViewWithTag("keyuses.status."+index);
        TextView number=root.findViewWithTag("keyuses.number."+index);
        assertEquals(label,chip.getText().toString());assertEquals(colour,chip.getCurrentTextColor());
        assertSame(number.getParent(),chip.getParent());
    }
    @Test public void bothFamiliesRestartNumberingAndEveryStatusFitsThePhone()throws Exception {
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).get();
        List<KeyAudit.Row> rows=new ArrayList<>();
        for(int i=1;i<=96;i++)rows.add(new KeyAudit.Row(i,"Mx"+"a".repeat(60),262144,999999,9999999,i==3,i<=2,i==1?4:1,i>64?KeyAudit.Family.BLOCK:KeyAudit.Family.CLASSIC,true,true));
        View root=build(activity,rows);
        assertEquals(66,((ViewGroup)root.findViewWithTag("keyuses.section.0")).getChildCount());
        assertEquals(34,((ViewGroup)root.findViewWithTag("keyuses.section.1")).getChildCount());
        assertEquals("#64",((TextView)root.findViewWithTag("keyuses.number.64")).getText().toString());
        assertEquals("#1 · 128/4",((TextView)root.findViewWithTag("keyuses.number.65")).getText().toString());
        assertEquals("#32 · 128/4",((TextView)root.findViewWithTag("keyuses.number.96")).getText().toString());
        assertChip(root,1,"RE-USED ×4",Design.RISK);assertChip(root,2,"RE-USED ×1",Design.WARN);
        assertChip(root,3,"AT RISK",Design.RISK);assertChip(root,4,"Used · 999,999",Design.OK);
        noHorizontalScroll(root);
        for(int dp:new int[]{320,480,900}) {
            int width=Math.round(dp*activity.getResources().getDisplayMetrics().density);
            root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            root.layout(0,0,width,root.getMeasuredHeight());
            View number=root.findViewWithTag("keyuses.number.65");
            ViewGroup address=(ViewGroup)number.getParent(),row=(ViewGroup)address.getParent();
            assertEquals(4,row.getChildCount());assertTrue(address.getWidth()>0);
            assertTrue(row.getChildAt(3).getRight()<=row.getWidth());
        }
    }
    @Test public void groupingInterleavedKeysPreservesAuditIdentity()throws Exception {
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).get();
        View root=build(activity,Arrays.asList(new KeyAudit.Row(1,"Mxblock",4,4,4,false,true,5,KeyAudit.Family.BLOCK,true,true),new KeyAudit.Row(2,"Mxclassic",4,4,4,false,false,0,KeyAudit.Family.CLASSIC,true,true)));
        ViewGroup classic=root.findViewWithTag("keyuses.section.0"),blocks=root.findViewWithTag("keyuses.section.1");
        assertNotNull(classic.findViewWithTag("keyuses.number.2"));assertNull(classic.findViewWithTag("keyuses.number.1"));
        assertNotNull(blocks.findViewWithTag("keyuses.number.1"));assertEquals("#1",((TextView)classic.findViewWithTag("keyuses.number.2")).getText().toString());
        assertChip(root,1,"RE-USED ×5",Design.RISK);
    }
    @Test public void partialRowsAreAmberWhileKnownPublicUseRemainsVisible()throws Exception {
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).get();
        List<KeyAudit.Row> rows=Arrays.asList(
            new KeyAudit.Row(1,"Mxunknown",0,0,0,false,false,0,KeyAudit.Family.CLASSIC,false,false),
            new KeyAudit.Row(2,"Mxused",-1,35,40,false,false,0,KeyAudit.Family.BLOCK,false,true));
        View root=build(activity,rows);
        assertChip(root,1,"Unknown",Design.WARN);
        assertChip(root,2,"Used · 35",Design.OK);
        KeyAudit.Result result=new KeyAudit.Result(rows,KeyAudit.Verdict.OK,0,256,false,false);
        Method m=MainActivity.class.getDeclaredMethod("verdictCard",KeyAudit.Result.class);m.setAccessible(true);
        ViewGroup verdict=(ViewGroup)m.invoke(activity,result);
        TextView title=(TextView)verdict.getChildAt(0);
        assertEquals("Audit needs review",title.getText().toString());assertEquals(Design.WARN,title.getCurrentTextColor());
        assertFalse(containsText(verdict,Copy.RECO_LABEL)); // Block-family explanation is still allowed.
    }
    @Test public void invalidNodeCounterIsNeverCoercedToZero()throws Exception {
        Method m=MainActivity.class.getDeclaredMethod("exactInt",org.json.JSONObject.class,String.class,int.class);m.setAccessible(true);
        for(Object value:new Object[]{org.json.JSONObject.NULL,"oops",0.5,"2147483648"}) {
            assertEquals(-1,m.invoke(null,new org.json.JSONObject().put("uses",value),"uses",-1));
        }
        assertEquals(-1,m.invoke(null,new org.json.JSONObject(),"uses",-1));
        assertEquals(35,m.invoke(null,new org.json.JSONObject().put("uses",35),"uses",-1));
    }

    private static boolean containsText(View view,String value) {
        if(view instanceof TextView && ((TextView)view).getText().toString().contains(value))return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)
            if(containsText(((ViewGroup)view).getChildAt(i),value))return true;
        return false;
    }

}
