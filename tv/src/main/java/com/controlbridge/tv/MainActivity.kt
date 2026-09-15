package com.controlbridge.tv

import android.app.Activity
import android.os.Bundle
import com.controlbridge.tv.network.TvBridgeClient
import com.controlbridge.tv.ui.TvDashboardView

class MainActivity:Activity(){private lateinit var view:TvDashboardView;private lateinit var client:TvBridgeClient
 override fun onCreate(b:Bundle?){super.onCreate(b);window.setFlags(1024,1024);view=TvDashboardView(this);setContentView(view);client=TvBridgeClient(this);client.onState={s->runOnUiThread{view.update(s)}};client.onPacket={p->runOnUiThread{view.input(p)}};client.start()}
 override fun onDestroy(){client.stop();super.onDestroy()}}
