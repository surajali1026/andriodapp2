package com.surajali.showme

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    companion object { init { System.loadLibrary("showme_native") } }
    private external fun nativeCrossedAbove(previous: Double, current: Double, target: Double): Boolean
    private external fun nativeCrossedBelow(previous: Double, current: Double, target: Double): Boolean

    private val bg=Color.rgb(13,18,27)
    private val panel=Color.rgb(24,32,45)
    private val muted=Color.rgb(145,158,176)
    private lateinit var body:LinearLayout
    private var symbol="BTCUSD"
    private val symbols=mutableListOf("BTCUSD","ETHUSD","XAUUSD","EURUSD","GBPUSD","USDJPY")
    private val prefs by lazy { getSharedPreferences("alerts",MODE_PRIVATE) }

    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun txt(s:String,size:Float=14f,color:Int=Color.WHITE,bold:Boolean=false)=TextView(this).apply { text=s;textSize=size;setTextColor(color);if(bold)setTypeface(typeface,1) }
    private fun card()=LinearLayout(this).apply { orientation=1;setPadding(dp(12),dp(12),dp(12),dp(12));setBackgroundColor(panel) }
    private fun gap(parent:LinearLayout,h:Int=8){parent.addView(android.view.View(this),LinearLayout.LayoutParams(1,dp(h)))}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);screen("Quotes")}

    private fun screen(tab:String){
        val root=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(18),dp(16),dp(8));setBackgroundColor(bg)}
        val head=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
        head.addView(txt("SHOWME",20f,Color.WHITE,true),LinearLayout.LayoutParams(0,dp(36),1f))
        head.addView(txt("● TERMINAL",10f,Color.rgb(40,190,130),true));root.addView(head);gap(root,12)
        body=LinearLayout(this).apply{orientation=1};root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        when(tab){"Quotes"->quotes();"Chart"->chart();"Alerts"->alerts();else->settings()}
        val nav=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER}
        listOf("Quotes","Chart","Alerts","Settings").forEach{name->
            val b=TextView(this).apply{text=name;gravity=Gravity.CENTER;textSize=11f;setTextColor(if(tab==name)Color.WHITE else muted);setPadding(0,dp(14),0,dp(14));setOnClickListener{screen(name)}}
            nav.addView(b,LinearLayout.LayoutParams(0,dp(50),1f))
        }
        root.addView(nav);setContentView(root)
    }
    private fun quotes(){
        body.addView(txt("Market Watch",23f,Color.WHITE,true));body.addView(txt("Symbols",12f,muted));gap(body,10)
        val search=EditText(this).apply{hint="Search symbol";setHintTextColor(muted);setTextColor(Color.WHITE);singleLine=true;setBackgroundColor(panel)}
        body.addView(search,LinearLayout.LayoutParams(-1,dp(46)));gap(body,8)
        val rows=LinearLayout(this).apply{orientation=1};val scroll=ScrollView(this);scroll.addView(rows);body.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        fun fill(q:String=""){rows.removeAllViews();symbols.filter{it.contains(q,true)}.forEach{s->
            val r=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12));setBackgroundColor(panel);setOnClickListener{symbol=s;screen("Chart")}}
            r.addView(txt(s,15f,Color.WHITE,true),LinearLayout.LayoutParams(0,dp(38),1f));r.addView(txt("--   /   --",13f,muted));rows.addView(r);gap(rows,2)
        }}
        fill()
        search.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){fill(s?.toString()?: "")}
            override fun afterTextChanged(s:android.text.Editable?){}
        })
        body.addView(Button(this).apply{text="+ Add symbol";isAllCaps=false;setOnClickListener{
            val input=EditText(this@MainActivity).apply{hint="Symbol";setTextColor(Color.WHITE)}
            android.app.AlertDialog.Builder(this@MainActivity).setTitle("Add symbol").setView(input).setPositiveButton("Add"){_,_->
                val v=input.text.toString().trim().uppercase();if(v.isNotEmpty()&&!symbols.contains(v)){symbols.add(v);fill(search.text.toString())}
            }.setNegativeButton("Cancel",null).show()
        }},LinearLayout.LayoutParams(-1,dp(46)))
    }
    private fun chart(){
        body.addView(txt(symbol,22f,Color.WHITE,true));body.addView(txt("Chart terminal",12f,muted));gap(body,10)
        val prices=card();val line=LinearLayout(this).apply{orientation=0}
        listOf("LAST PRICE","MARK PRICE","INDEX PRICE").forEach{p->val c=LinearLayout(this).apply{orientation=1;addView(txt(p,9f,muted,true));addView(txt("--",17f,Color.WHITE,true))};line.addView(c,LinearLayout.LayoutParams(0,-2,1f))}
        prices.addView(line);body.addView(prices);gap(body,8)
        val tf=LinearLayout(this).apply{orientation=0}
        listOf("M1","M5","M15","M30","H1","H4","D1").forEach{t->tf.addView(txt(t,10f,Color.WHITE).apply{gravity=Gravity.CENTER;setPadding(dp(5),dp(10),dp(5),dp(10));setBackgroundColor(panel)},LinearLayout.LayoutParams(0,dp(36),1f))}
        body.addView(tf);gap(body,8)
        val chart=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER;setBackgroundColor(Color.rgb(9,14,22));addView(txt("CHART WORKSPACE",13f,muted,true));addView(txt("Candles will be added on your separate command.",11f,muted))}
        body.addView(chart,LinearLayout.LayoutParams(-1,0,1f));gap(body,8)
        body.addView(Button(this).apply{text="+ Create price alert";isAllCaps=false;setOnClickListener{screen("Alerts")}},LinearLayout.LayoutParams(-1,dp(46)))
    }
    private fun alerts(){
        body.addView(txt("Price Alerts",23f,Color.WHITE,true));body.addView(txt("Create and manage alert rules",12f,muted));gap(body,10)
        val box=card();box.addView(txt("SYMBOL",10f,muted,true))
        val sym=EditText(this).apply{setText(symbol);setTextColor(Color.WHITE);singleLine=true};box.addView(sym)
        box.addView(txt("TRIGGER PRICE",10f,muted,true))
        val price=EditText(this).apply{hint="Enter target price";setHintTextColor(muted);setTextColor(Color.WHITE);inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL};box.addView(price)
        val cond=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Crosses above","Crosses below"))};box.addView(cond)
        box.addView(Button(this).apply{text="Save alert";isAllCaps=false;setOnClickListener{
            val s=sym.text.toString().trim().uppercase();val p=price.text.toString().trim()
            if(s.isEmpty()||p.toDoubleOrNull()==null)Toast.makeText(this@MainActivity,"Enter symbol and valid price",Toast.LENGTH_SHORT).show()
            else{prefs.edit().putString("alert_"+System.currentTimeMillis(),s+"|"+p+"|"+cond.selectedItem.toString()).apply();screen("Alerts");Toast.makeText(this@MainActivity,"Alert rule saved on device",Toast.LENGTH_SHORT).show()}
        }},LinearLayout.LayoutParams(-1,dp(44)))
        body.addView(box);gap(body,12);body.addView(txt("SAVED ALERTS",11f,muted,true));gap(body,6)
        val list=LinearLayout(this).apply{orientation=1};val scroll=ScrollView(this);scroll.addView(list)
        prefs.all.filterKeys{it.startsWith("alert_")}.forEach{(k,v)->
            val parts=(v as? String?:"").split("|")
            if(parts.size==3){val row=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(8),dp(8),dp(8));setBackgroundColor(panel)}
                row.addView(txt(parts[0]+"  "+parts[1]+"\n"+parts[2],13f,Color.WHITE),LinearLayout.LayoutParams(0,-2,1f))
                row.addView(txt("✕",18f,Color.RED).apply{setPadding(dp(12),dp(8),dp(12),dp(8));setOnClickListener{prefs.edit().remove(k).apply();screen("Alerts")}})
                list.addView(row);gap(list,4)
            }
        }
        if(list.childCount==0)list.addView(txt("No saved alerts.",12f,muted))
        body.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));gap(body,5)
        body.addView(txt("Native C++ alert-crossing engine is connected; live price feed is not connected yet.",10f,muted))
    }
    private fun settings(){
        body.addView(txt("Settings",23f,Color.WHITE,true));gap(body,12)
        listOf("Appearance     Dark terminal","Price display     Last / Mark / Index","Notifications     Price alerts","Native engine     C++ (NDK)").forEach{body.addView(card().apply{addView(txt(it,14f))});gap(body,6)}
    }
}
