@echo off
cd /d "%~dp0backend"
if not exist .venv\Scripts\python.exe python -m venv .venv
call .venv\Scripts\activate.bat
python -m pip install -q -r requirements.txt
python server.py --host 0.0.0.0 --port 8000
pause
