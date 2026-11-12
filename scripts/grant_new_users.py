"""
给某段时间内注册的用户批量开通模块（走后台接口，不用部署、不用连数据库）。

到期时间 = 每个人的注册日期 + N 天（当天 23:59:59），或统一指定到期日期；
已有更晚的到期时间就跳过，不会缩短，也不会重复叠加天数。默认先预览，确认后才执行。

用法：
  python3 scripts/grant_new_users.py                               # 今天注册的用户，政治 + 教育学，365 天
  python3 scripts/grant_new_users.py --from 2026-11-10 --to 2026-11-12
  python3 scripts/grant_new_users.py --modules 政治,教育学,英语 --days 180
  python3 scripts/grant_new_users.py --until 2027-12-31
运行时提示输入后台 admin 密码（输入时不显示）。
"""
import argparse, datetime as dt, getpass, json, os, urllib.parse, urllib.request

BASE = os.environ.get('LEMON_ADMIN_BASE', 'http://106.15.45.85:8091/api/admin')
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(method, path, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    r = json.loads(opener.open(req, timeout=60).read())
    if r['code'] != 0:
        raise RuntimeError(f'{method} {path}: {r["msg"]}')
    return r['data']


def parse_time(s):
    return dt.datetime.strptime(s, '%Y-%m-%d %H:%M:%S') if s else None


today = dt.date.today()
ap = argparse.ArgumentParser(description='给某段时间内注册的用户批量开通模块')
ap.add_argument('--from', dest='date_from', type=dt.date.fromisoformat, default=today, help='注册开始日期，默认今天')
ap.add_argument('--to', dest='date_to', type=dt.date.fromisoformat, default=today, help='注册结束日期（含），默认今天')
ap.add_argument('--modules', default='政治,教育学', help='模块名，逗号分隔，默认 政治,教育学')
g = ap.add_mutually_exclusive_group()
g.add_argument('--days', type=int, default=365, help='注册日期往后 N 天，默认 365')
g.add_argument('--until', type=dt.date.fromisoformat, help='统一到期日期（当天 23:59:59）')
ap.add_argument('--yes', action='store_true', help='不预览直接执行')
args = ap.parse_args()

password = os.environ.get('LEMON_ADMIN_PASSWORD') or getpass.getpass('后台 admin 密码：')
token = call('POST', '/auth/login', {'username': 'admin', 'password': password})['token']

modules = {m['name']: m['id'] for m in call('GET', '/modules', token=token)}
names = [n.strip() for n in args.modules.split(',') if n.strip()]
missing = [n for n in names if n not in modules]
if missing:
    raise SystemExit(f'没有这些模块：{missing}，现有模块：{list(modules)}')

# 用户列表按 id 倒序（新注册的在前），翻到注册时间早于开始日期就停
start = dt.datetime.combine(args.date_from, dt.time.min)
end = dt.datetime.combine(args.date_to + dt.timedelta(days=1), dt.time.min)
users, page, done = [], 1, False
while not done:
    r = call('GET', f'/app-users?pageNum={page}&pageSize=200', token=token)
    for u in r['list']:
        created = parse_time(u.get('createdAt'))
        if created is None or created < start:
            done = True
            break
        if created < end:
            users.append(u)
    if page * 200 >= r['total']:
        break
    page += 1

plan = []  # (user, 模块名, 当前到期, 新到期日期)
for u in reversed(users):
    created = parse_time(u['createdAt'])
    until = args.until or (created + dt.timedelta(days=args.days)).date()
    if until < today:
        continue
    target = dt.datetime.combine(until, dt.time(23, 59, 59))
    current = {m['moduleName']: parse_time(m['expireAt']) for m in call('GET', f'/app-users/{u["id"]}/access', token=token)['modules']}
    for n in names:
        cur = current.get(n)
        if cur is None or cur < target:
            plan.append((u, n, cur, until))

print(f'{args.date_from} ~ {args.date_to} 注册的用户 {len(users)} 人，需要开通 / 延长 {len(plan)} 条：')
for u, n, cur, until in plan:
    print(f'  {u["phone"] or u["username"]:<12} {u["nickname"] or "":<10} {n}  {cur or "未开通"} → {until} 23:59:59')
if not plan:
    raise SystemExit('没有需要处理的')
if not args.yes and input('确认执行？输入 y 继续：').strip().lower() != 'y':
    raise SystemExit('已取消')

ok = 0
for u, n, cur, until in plan:
    try:
        call('POST', f'/app-users/{u["id"]}/access/grant',
             {'moduleId': modules[n], 'until': until.isoformat(), 'remark': '新用户批量开通'}, token)
        ok += 1
    except Exception as e:
        print(f'失败  {u["phone"] or u["username"]} {n}：{e}')
print(f'完成：成功 {ok} / {len(plan)}')
