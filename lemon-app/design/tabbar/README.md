# 底部 Tab 图标

`*.svg` 是源文件（24 网格，线宽 1.8；未选中为线框、选中为实心），用到的颜色和 `src/theme.json` 的 tabColor / tabSelectedColor 一致。
导出的 PNG 在 `src/static/tabbar/`（162×162，透明背景；小程序 tab 图标只能用 PNG / JPG，单个不超过 40KB）。

改图标后重新导出（headless Chrome，透明背景）：

```bash
for f in *.svg; do n=${f%.svg}; "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --hide-scrollbars --default-background-color=00000000 --force-device-scale-factor=2 --window-size=81,81 --screenshot=../../src/static/tabbar/$n.png "file://$PWD/$f"; done
```
