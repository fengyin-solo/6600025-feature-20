<script setup lang="ts">
import { computed, ref } from 'vue';
import VChart from 'vue-echarts';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { BarChart, LineChart } from 'echarts/charts';
import {
  GridComponent,
  TooltipComponent,
  LegendComponent
} from 'echarts/components';
import { useCanBusStore } from '../store/canbus';

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent]);

const store = useCanBusStore();
const chartRef = ref<InstanceType<typeof VChart> | null>(null);

const chartOption = computed(() => {
  const trend = store.trafficTrend;
  const times = trend.map(p => p.time);
  const rxData = trend.map(p => [p.time, p.rx]);
  const txData = trend.map(p => [p.time, p.tx]);
  const diffData = trend.map(p => [p.time, p.rx - p.tx]);

  return {
    backgroundColor: '#111827',
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#1f2937',
      borderColor: '#374151',
      textStyle: { color: '#e5e7eb', fontSize: 12 },
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        if (!Array.isArray(params) || params.length === 0) return '';
        const time = new Date(params[0].value[0]).toLocaleTimeString('zh-CN', { hour12: false });
        let html = `<div style="font-size:11px;color:#9ca3af">${time}</div>`;
        for (const p of params) {
          const val = Number(p.value[1]);
          const displayVal = p.seriesName === 'RX-TX差'
            ? (val > 0 ? `+${val}` : `${val}`)
            : `${val}`;
          html += `<div style="display:flex;align-items:center;gap:6px">
            <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${p.color}"></span>
            <span>${p.seriesName}: <b>${displayVal}</b></span>
          </div>`;
        }
        const rx = params.find((p: any) => p.seriesName === 'RX(接收)')?.value[1] || 0;
        const tx = params.find((p: any) => p.seriesName === 'TX(发送)')?.value[1] || 0;
        const sum = rx + tx;
        if (sum > 0) {
          const ratio = tx === 0 ? '∞' : (rx / tx).toFixed(2);
          html += `<div style="margin-top:4px;padding-top:4px;border-top:1px solid #374151;font-size:11px;color:#9ca3af">
            RX/TX比率: <b style="color:#eab308">${ratio}</b>
          </div>`;
        }
        return html;
      }
    },
    legend: {
      top: 8,
      textStyle: { color: '#9ca3af', fontSize: 11 },
      itemWidth: 12,
      itemHeight: 2
    },
    grid: {
      left: 50,
      right: 20,
      top: 45,
      bottom: 35
    },
    xAxis: {
      type: 'category',
      data: times,
      axisLabel: {
        color: '#6b7280',
        fontSize: 10,
        formatter: (val: number) => {
          const d = new Date(val);
          return d.toLocaleTimeString('zh-CN', { hour12: false });
        }
      },
      axisLine: { lineStyle: { color: '#374151' } },
      splitLine: { lineStyle: { color: '#1f2937' } }
    },
    yAxis: [
      {
        type: 'value',
        name: '帧数/秒',
        nameTextStyle: { color: '#6b7280', fontSize: 10 },
        axisLabel: { color: '#6b7280', fontSize: 10 },
        axisLine: { lineStyle: { color: '#374151' } },
        splitLine: { lineStyle: { color: '#1f2937' } }
      },
      {
        type: 'value',
        name: '差值',
        nameTextStyle: { color: '#6b7280', fontSize: 10 },
        axisLabel: { color: '#6b7280', fontSize: 10 },
        axisLine: { lineStyle: { color: '#374151' } },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: 'RX(接收)',
        type: 'bar',
        stack: 'traffic',
        itemStyle: { color: '#22c55e' },
        emphasis: { focus: 'series' },
        data: rxData,
        barWidth: '40%'
      },
      {
        name: 'TX(发送)',
        type: 'bar',
        stack: 'traffic',
        itemStyle: { color: '#3b82f6' },
        emphasis: { focus: 'series' },
        data: txData
      },
      {
        name: 'RX-TX差',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 2, color: '#f59e0b' },
        itemStyle: { color: '#f59e0b' },
        data: diffData,
        markLine: {
          symbol: 'none',
          lineStyle: { color: '#ef4444', type: 'dashed', width: 1 },
          label: { color: '#ef4444', fontSize: 10, formatter: '0基准' },
          data: [{ yAxis: 0 }]
        }
      }
    ]
  };
});
</script>

<template>
  <div class="flex flex-col h-full bg-gray-900 rounded-lg overflow-hidden">
    <div class="px-4 py-2 bg-gray-800 border-b border-gray-700 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <h3 class="text-sm font-semibold text-gray-300">收发流量趋势</h3>
        <span
          class="px-1.5 py-0.5 rounded text-xs font-bold"
          :class="{
            'bg-green-900/50 text-green-400': store.trafficImbalanceStatus.level === 'normal',
            'bg-yellow-900/50 text-yellow-400': store.trafficImbalanceStatus.level === 'warning',
            'bg-red-900/50 text-red-400': store.trafficImbalanceStatus.level === 'severe'
          }"
        >
          {{ store.trafficImbalanceStatus.label }}
        </span>
      </div>
      <div class="flex items-center gap-3 text-xs text-gray-500">
        <span>采样窗口: 1秒</span>
        <span>点数: {{ store.trafficTrend.length }}/60</span>
        <span class="text-yellow-400">RX/TX比: {{ store.rxTxRatio === 99.99 ? '∞' : store.rxTxRatio }}</span>
      </div>
    </div>
    <div class="flex-1 p-2">
      <VChart
        ref="chartRef"
        :option="chartOption"
        autoresize
        class="w-full h-full"
        style="min-height: 200px;"
      />
    </div>
    <div v-if="store.trafficTrend.length === 0" class="absolute inset-0 flex items-center justify-center pointer-events-none">
      <p class="text-gray-600 text-sm">开始捕获以显示流量趋势...</p>
    </div>
  </div>
</template>
