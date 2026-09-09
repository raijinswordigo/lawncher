#ifndef LAWNCHER_PATHPARSER_H
#define LAWNCHER_PATHPARSER_H

#include <stdio.h>

const char *parse_path(const char *vpath, int *readonly);
FILE *parse_open(const char *vpath, const char *mode);

#endif
