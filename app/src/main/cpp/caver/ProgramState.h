#ifndef LAWNCHER_PROGRAMSTATE_H
#define LAWNCHER_PROGRAMSTATE_H

#include "../lua/lua.h"

typedef struct ProgramState {
    lua_State *L;
} ProgramState;

#endif //LAWNCHER_PROGRAMSTATE_H
