@echo off
setlocal
set path=%path%;D:\Programy\Inkscape
set inpath=.
set outpath=..\..\res
call :export_files
endlocal
goto :EOF

:export_files
set filename=ic_stop_normal
call :export_image
set filename=ic_stop_disabled
call :export_image
set filename=ic_play
call :export_image
set filename=ic_cancel_normal
call :export_image
set filename=ic_cancel_disabled
call :export_image
goto :EOF

:export_image
set infile=%inpath%\%filename%.svg
set outfile=%outpath%\drawable-ldpi\%filename%.png
set size=12
call :export_single_file
set outfile=%outpath%\drawable-mdpi\%filename%.png
set size=16
call :export_single_file
set outfile=%outpath%\drawable-hdpi\%filename%.png
set size=24
call :export_single_file
set outfile=%outpath%\drawable-xhdpi\%filename%.png
set size=32
call :export_single_file
goto :EOF

:export_single_file
rm %outfile%
echo inkscape.exe --export-png=%outfile% --export-area-page  --export-width %size% --export-height %size% %infile%
inkscape.exe --export-png=%outfile% --export-area-page  --export-width %size% --export-height %size% %infile%
goto :EOF
