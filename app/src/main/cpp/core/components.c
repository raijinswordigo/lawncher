#include "components.h"
#include "hook.h"

void *CharController_Interface;
void *CharAnimController_Interface;
void *SwingableWeaponController_Interface;
void *Model_Interface;

void init_components() {
	CharController_Interface = swordigo_dlsym("_ZN5Caver23CharControllerComponent9InterfaceEv");
	CharAnimController_Interface = swordigo_dlsym("_ZN5Caver27CharAnimControllerComponent9InterfaceEv");
	Model_Interface = swordigo_dlsym("_ZN5Caver14ModelComponent9InterfaceEv");
	SwingableWeaponController_Interface = swordigo_dlsym("_ZN5Caver34SwingableWeaponControllerComponent9InterfaceEv");
}