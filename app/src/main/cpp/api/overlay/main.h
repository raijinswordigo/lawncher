#ifndef LAWNCHER_MAIN_H
#define LAWNCHER_MAIN_H

#include "lua.h"

void API_register_java_stuff(lua_State *L);
void initAPI_java(void);

void button_remove_all(void);

#endif //LAWNCHER_MAIN_H
