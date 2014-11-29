@echo off
setlocal
set path=%path%;D:\Programy\Inkscape
set inpath=.
set outpath=..\..\res
call :export_files
endlocal
goto :EOF

:export_files
set filename=cache_type_event
call :export_image
set filename=cache_type_moving
call :export_image
set filename=cache_type_multi
call :export_image
set filename=cache_type_own
call :export_image
set filename=cache_type_quiz
call :export_image
set filename=cache_type_traditional
call :export_image
set filename=cache_type_unknown
call :export_image
set filename=cache_type_virtual
call :export_image
set filename=cache_type_webcam
call :export_image
set filename=cache_type_math
call :export_image
set filename=cache_type_drive_in
call :export_image
goto :EOF

:export_image
set infile=%inpath%\%filename%.svg
set outfile=%outpath%\drawable-ldpi\%filename%.png
set size=18
call :export_single_file
set outfile=%outpath%\drawable-mdpi\%filename%.png
set size=24
call :export_single_file
set outfile=%outpath%\drawable-hdpi\%filename%.png
set size=36
call :export_single_file
set outfile=%outpath%\drawable-xhdpi\%filename%.png
set size=48
call :export_single_file
goto :EOF

:export_single_file
rm %outfile%
echo inkscape.exe --export-png=%outfile% --export-area-page  --export-width %size% --export-height %size% %infile%
inkscape.exe --export-png=%outfile% --export-area-page  --export-width %size% --export-height %size% %infile%
goto :EOF
