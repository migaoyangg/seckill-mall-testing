const state = {projects: [], environments: [], suites: [], schedules: [], runs: [], devices: [], token: localStorage.getItem('testflow_token') || '', user: null};
let activeLogSocket = null;
const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];

async function api(path, options = {}) {
  const headers = {'Content-Type': 'application/json', ...(options.headers || {})};
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  const response = await fetch(path, {...options, headers});
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    if (response.status === 401 && path !== '/api/auth/login') showLogin();
    throw new Error(body.detail || `请求失败：${response.status}`);
  }
  return response.json();
}

function toast(message) {
  const element = $('#toast');
  element.textContent = message;
  element.classList.add('show');
  setTimeout(() => element.classList.remove('show'), 2600);
}

function statusBadge(status) {
  const labels = {PENDING:'等待',RUNNING:'运行中',PASSED:'通过',FAILED:'失败',TIMEOUT:'超时',CANCELLED:'已取消',SKIPPED:'跳过'};
  return `<span class="status status-${status}">${labels[status] || status}</span>`;
}

function formatDate(value) {
  if (!value) return '-';
  return new Date(`${value}Z`).toLocaleString('zh-CN', {hour12:false});
}

function escapeHtml(value = '') {
  return String(value).replace(/[&<>'"]/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]));
}

function switchView(name) {
  $$('.view').forEach(view => view.classList.toggle('active', view.id === `${name}View`));
  $$('.nav-item').forEach(item => item.classList.toggle('active', item.dataset.view === name));
  $('#pageTitle').textContent = {dashboard:'质量概览',runs:'测试任务',config:'项目配置',devices:'设备中心'}[name];
  if (name === 'runs') loadRuns();
  if (name === 'config') loadConfig();
  if (name === 'devices') loadDevices();
}

function showLogin() { $('#loginScreen').classList.remove('hidden'); }
function hideLogin() { $('#loginScreen').classList.add('hidden'); }

function applyUser(user) {
  state.user = user;
  $('#currentUserName').textContent = user.display_name;
  $('#currentUserRole').textContent = {ADMIN:'管理员',TESTER:'测试人员',VIEWER:'只读访客'}[user.role] || user.role;
  const readOnly = user.role === 'VIEWER';
  $('#quickRunBtn').classList.toggle('hidden', readOnly);
  $('#newRunBtn').classList.toggle('hidden', readOnly);
  $('.config-form-panel').classList.toggle('hidden', user.role !== 'ADMIN');
  $('#scanDevicesBtn').classList.toggle('hidden', readOnly);
}

async function loadHealth() {
  try { await api('/api/health'); $('#healthText').textContent = '平台服务正常'; }
  catch { $('#healthText').textContent = '平台服务异常'; }
}

async function loadDashboard() {
  const data = await api('/api/dashboard');
  $('#totalRuns').textContent = data.total_runs;
  $('#passRate').textContent = `${data.average_pass_rate}%`;
  $('#avgDuration').textContent = `${data.average_duration}s`;
  $('#runningRuns').textContent = data.running_runs;

  const chart = $('#trendChart');
  if (data.recent_runs.length) {
    chart.classList.remove('empty');
    chart.innerHTML = data.recent_runs.slice().reverse().map(run => `<div class="bar-wrap" title="${run.run_no} · ${run.pass_rate}%"><div class="bar" style="height:${Math.max(run.pass_rate, 4)}%"></div><small>${run.pass_rate}%</small></div>`).join('');
  } else { chart.classList.add('empty'); chart.textContent = '暂无执行数据'; }

  const failures = $('#failureList');
  if (data.frequent_failures.length) {
    failures.classList.remove('empty');
    failures.innerHTML = data.frequent_failures.map(item => `<div class="failure-item"><strong>${escapeHtml(item.case_name)}</strong><span>${item.count}次</span></div>`).join('');
  } else { failures.classList.add('empty'); failures.textContent = '暂无失败记录'; }

  $('#recentRunsBody').innerHTML = data.recent_runs.length ? data.recent_runs.map(run => `<tr><td><button class="link-button" onclick="openRun(${run.id})">${run.run_no}</button></td><td>${statusBadge(run.status)}</td><td>${run.pass_rate}%</td><td>${run.duration}s</td><td>${formatDate(run.created_at)}</td></tr>`).join('') : '<tr><td colspan="5" class="empty">暂无任务，点击右上角创建</td></tr>';
}

async function loadConfig() {
  [state.projects, state.environments, state.suites, state.schedules] = await Promise.all([api('/api/projects'), api('/api/environments'), api('/api/suites'), api('/api/schedules')]);
  $('#projectList').innerHTML = state.projects.map(item => `<div class="config-item"><strong>${escapeHtml(item.name)}</strong><small>${escapeHtml(item.code)} · ${escapeHtml(item.work_dir)}</small></div>`).join('');
  $('#environmentList').innerHTML = state.environments.map(item => `<div class="config-item"><strong>${escapeHtml(item.name)}</strong><small>${escapeHtml(item.base_url || '无服务地址')}</small></div>`).join('') || '<div class="empty">暂无环境</div>';
  $('#suiteList').innerHTML = state.suites.map(item => `<div class="config-item"><strong>${escapeHtml(item.name)}</strong><small>${escapeHtml(item.test_type)} · ${escapeHtml(item.test_path)} ${item.marker ? `· ${escapeHtml(item.marker)}` : ''}</small></div>`).join('') || '<div class="empty">暂无套件</div>';
  $('#scheduleList').innerHTML = state.schedules.map(item => `<div class="config-item"><strong>${escapeHtml(item.name)} <span class="status ${item.enabled ? 'status-PASSED' : 'status-CANCELLED'}">${item.enabled ? '已启用' : '已暂停'}</span></strong><small>每 ${item.interval_minutes} 分钟 · 下次 ${formatDate(item.next_run_at)} · <button class="link-button" onclick="toggleSchedule(${item.id})">${item.enabled ? '暂停' : '启用'}</button></small></div>`).join('') || '<div class="empty">暂无定时计划</div>';
  $$('.project-select').forEach(select => select.innerHTML = state.projects.map(project => `<option value="${project.id}">${escapeHtml(project.name)}</option>`).join(''));
  $('#scheduleEnvironment').innerHTML = state.environments.map(item => `<option value="${item.id}">${escapeHtml(item.name)}</option>`).join('');
  filterScheduleSuites();
}

async function loadRuns() {
  state.runs = await api('/api/runs');
  $('#runsBody').innerHTML = state.runs.length ? state.runs.map(run => {
    const result = `${run.passed_count}/${run.total_count} 通过`;
    const canOperate = state.user && state.user.role !== 'VIEWER';
    const cancellable = canOperate && ['PENDING','RUNNING'].includes(run.status) ? `<button class="link-button danger-button" onclick="cancelRun(${run.id})">取消</button>` : '';
    const retryable = canOperate && ['FAILED','TIMEOUT','CANCELLED'].includes(run.status) ? `<button class="link-button" onclick="retryRun(${run.id})">重跑</button>` : '';
    return `<tr><td><button class="link-button" onclick="openRun(${run.id})">${run.run_no}</button>${run.retry_of_run_id ? `<small> ↳ 重跑 #${run.retry_of_run_id}</small>` : ''}</td><td>${statusBadge(run.status)}</td><td>${result}</td><td>${run.duration}s</td><td>${escapeHtml(run.created_by)}</td><td>${cancellable}${retryable}</td></tr>`;
  }).join('') : '<tr><td colspan="6" class="empty">暂无任务</td></tr>';
}

async function loadDevices() {
  state.devices = await api('/api/devices');
  $('#devicesBody').innerHTML = state.devices.length ? state.devices.map(device => `<tr><td>${escapeHtml(device.serial)}</td><td><span class="status status-${escapeHtml(device.status)}">${escapeHtml(device.status)}</span></td><td>${escapeHtml(device.model || device.product || '-')}</td><td>${escapeHtml(device.android_version || '-')}</td><td>${device.locked_by_run_id ? `#${device.locked_by_run_id}` : '空闲'}</td><td>${formatDate(device.last_seen_at)}</td></tr>`).join('') : '<tr><td colspan="6" class="empty">尚未扫描到设备</td></tr>';
}

async function scanDevices() {
  try { state.devices = await api('/api/devices/scan', {method:'POST'}); toast(`发现 ${state.devices.length} 台设备`); await loadDevices(); }
  catch (error) { toast(error.message); }
}

async function openRun(id) {
  switchView('runs');
  const run = await api(`/api/runs/${id}`);
  $('#runDetailSubtitle').textContent = `${run.run_no} · ${run.project_name} · ${run.suite_name}`;
  const passRate = run.total_count ? (run.passed_count / run.total_count * 100).toFixed(2) : '0.00';
  const artifacts = run.artifacts.map(item => `<a href="${item.file_path}" target="_blank">${escapeHtml(item.file_name)}</a>`).join('');
  const cases = run.case_results.map(item => `<tr><td>${escapeHtml(item.class_name)}</td><td>${escapeHtml(item.case_name)}</td><td>${statusBadge(item.status)}</td><td>${item.duration.toFixed(3)}s</td><td>${escapeHtml(item.error_message || '-')}</td></tr>`).join('');
  $('#runDetail').classList.remove('empty');
  $('#runDetail').innerHTML = `<div class="detail-grid"><div class="detail-stat"><small>状态</small><strong>${statusBadge(run.status)}</strong></div><div class="detail-stat"><small>用例数</small><strong>${run.total_count}</strong></div><div class="detail-stat"><small>通过率</small><strong>${passRate}%</strong></div><div class="detail-stat"><small>耗时</small><strong>${run.duration}s</strong></div><div class="detail-stat"><small>环境</small><strong>${escapeHtml(run.environment_name)}</strong></div></div>${run.error_message ? `<p class="hint">${escapeHtml(run.error_message)}</p>` : ''}<div class="artifact-links">${artifacts || '<span class="hint">任务完成后生成报告与日志</span>'}</div><h3>实时执行日志</h3><pre id="liveRunLog" class="live-log">正在连接日志流...</pre><div class="table-wrap case-list"><table><thead><tr><th>类</th><th>用例</th><th>状态</th><th>耗时</th><th>错误信息</th></tr></thead><tbody>${cases || '<tr><td colspan="5" class="empty">暂无用例结果</td></tr>'}</tbody></table></div>`;
  connectRunLog(id);
}

function connectRunLog(runId) {
  if (activeLogSocket) activeLogSocket.close();
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
  activeLogSocket = new WebSocket(`${protocol}://${location.host}/ws/runs/${runId}/logs?token=${encodeURIComponent(state.token)}`);
  activeLogSocket.onmessage = event => {
    const payload = JSON.parse(event.data);
    const element = $('#liveRunLog');
    if (!element) return;
    if (payload.type === 'history') element.textContent = payload.content || '等待任务输出...';
    if (payload.type === 'line') element.textContent += payload.content;
    element.scrollTop = element.scrollHeight;
  };
  activeLogSocket.onerror = () => {
    const element = $('#liveRunLog');
    if (element && element.textContent === '正在连接日志流...') element.textContent = '日志连接暂时不可用，可查看执行日志附件。';
  };
}

async function cancelRun(id) {
  if (!confirm('确定取消这个测试任务吗？')) return;
  try { await api(`/api/runs/${id}/cancel`, {method:'POST'}); toast('任务已取消'); await loadRuns(); await loadDashboard(); }
  catch (error) { toast(error.message); }
}

async function retryRun(id) {
  try { const run = await api(`/api/runs/${id}/retry`, {method:'POST'}); toast(`已创建重跑任务 ${run.run_no}`); await loadRuns(); await openRun(run.id); }
  catch (error) { toast(error.message); }
}

async function toggleSchedule(id) {
  try { await api(`/api/schedules/${id}/toggle`, {method:'POST'}); toast('定时计划状态已更新'); await loadConfig(); }
  catch (error) { toast(error.message); }
}

async function openRunDialog() {
  if (!state.environments.length || !state.suites.length) await loadConfig();
  $('#runEnvironment').innerHTML = state.environments.map(item => `<option value="${item.id}" data-project="${item.project_id}">${escapeHtml(item.name)}</option>`).join('');
  filterRunSuites();
  $('#runDialog').showModal();
}

function filterRunSuites() {
  const environment = state.environments.find(item => item.id === Number($('#runEnvironment').value));
  const suites = environment ? state.suites.filter(item => item.project_id === environment.project_id) : [];
  $('#runSuite').innerHTML = suites.map(item => `<option value="${item.id}">${escapeHtml(item.name)} · ${escapeHtml(item.test_type)}</option>`).join('');
}

function filterScheduleSuites() {
  const environment = state.environments.find(item => item.id === Number($('#scheduleEnvironment').value));
  const suites = environment ? state.suites.filter(item => item.project_id === environment.project_id) : [];
  $('#scheduleSuite').innerHTML = suites.map(item => `<option value="${item.id}">${escapeHtml(item.name)}</option>`).join('');
}

$('#runForm').addEventListener('submit', async event => {
  event.preventDefault();
  try {
    const run = await api('/api/runs', {method:'POST', body:JSON.stringify({environment_id:Number($('#runEnvironment').value), suite_id:Number($('#runSuite').value), created_by:$('#runCreator').value || 'local-user'})});
    $('#runDialog').close(); toast(`任务 ${run.run_no} 已进入队列`); switchView('runs'); await loadRuns(); await openRun(run.id);
  } catch (error) { toast(error.message); }
});

$('#environmentForm').addEventListener('submit', async event => {
  event.preventDefault(); const form = new FormData(event.target);
  try { await api('/api/environments', {method:'POST', body:JSON.stringify({project_id:Number(form.get('project_id')), name:form.get('name'), base_url:form.get('base_url'), variables:form.get('variables') ? JSON.parse(form.get('variables')) : {}})}); event.target.reset(); toast('环境已保存'); await loadConfig(); }
  catch (error) { toast(error.message); }
});

$('#suiteForm').addEventListener('submit', async event => {
  event.preventDefault(); const form = new FormData(event.target);
  try { await api('/api/suites', {method:'POST', body:JSON.stringify({project_id:Number(form.get('project_id')), name:form.get('name'), test_type:form.get('test_type'), test_path:form.get('test_path'), marker:form.get('marker'), timeout_seconds:Number(form.get('timeout_seconds')), device_required:form.get('test_type') === 'android'})}); event.target.reset(); toast('套件已保存'); await loadConfig(); }
  catch (error) { toast(error.message); }
});

$('#scheduleForm').addEventListener('submit', async event => {
  event.preventDefault(); const form = new FormData(event.target);
  try { await api('/api/schedules', {method:'POST', body:JSON.stringify({name:form.get('name'), environment_id:Number(form.get('environment_id')), suite_id:Number(form.get('suite_id')), interval_minutes:Number(form.get('interval_minutes')), created_by:'local-user'})}); event.target.reset(); toast('定时计划已保存'); await loadConfig(); }
  catch (error) { toast(error.message); }
});

$('#loginForm').addEventListener('submit', async event => {
  event.preventDefault();
  try {
    const result = await api('/api/auth/login', {method:'POST', body:JSON.stringify({username:$('#loginUsername').value, password:$('#loginPassword').value})});
    state.token = result.access_token; localStorage.setItem('testflow_token', state.token); applyUser(result.user); hideLogin(); await loadConfig(); await loadDashboard(); toast('登录成功');
  } catch (error) { toast(error.message); }
});

$('#logoutBtn').addEventListener('click', async () => {
  try { await api('/api/auth/logout', {method:'POST'}); } catch (_) {}
  if (activeLogSocket) activeLogSocket.close();
  state.token = ''; state.user = null; localStorage.removeItem('testflow_token'); showLogin();
});

$$('.nav-item').forEach(item => item.addEventListener('click', () => switchView(item.dataset.view)));
$$('[data-view-target]').forEach(item => item.addEventListener('click', () => switchView(item.dataset.viewTarget)));
$$('[data-action="refresh"]').forEach(item => item.addEventListener('click', loadDashboard));
$$('.tab').forEach(tab => tab.addEventListener('click', () => { $$('.tab').forEach(item => item.classList.remove('active')); tab.classList.add('active'); $('#environmentForm').classList.toggle('hidden', tab.dataset.form !== 'environment'); $('#suiteForm').classList.toggle('hidden', tab.dataset.form !== 'suite'); $('#scheduleForm').classList.toggle('hidden', tab.dataset.form !== 'schedule'); }));
$('#quickRunBtn').addEventListener('click', openRunDialog); $('#newRunBtn').addEventListener('click', openRunDialog);
$('#closeDialog').addEventListener('click', () => $('#runDialog').close()); $('#cancelDialog').addEventListener('click', () => $('#runDialog').close());
$('#runEnvironment').addEventListener('change', filterRunSuites);
$('#scheduleEnvironment').addEventListener('change', filterScheduleSuites);
$('#scanDevicesBtn').addEventListener('click', scanDevices);

window.openRun = openRun; window.cancelRun = cancelRun; window.retryRun = retryRun; window.toggleSchedule = toggleSchedule;

async function initialize() {
  await loadHealth();
  if (!state.token) { showLogin(); return; }
  try { const user = await api('/api/auth/me'); applyUser(user); hideLogin(); await loadConfig(); await loadDashboard(); }
  catch (_) { state.token = ''; localStorage.removeItem('testflow_token'); showLogin(); return; }
  setInterval(async () => { if (!state.token) return; await loadDashboard(); if ($('#runsView').classList.contains('active')) await loadRuns(); if ($('#devicesView').classList.contains('active')) await loadDevices(); }, 5000);
}
initialize().catch(error => toast(error.message));
