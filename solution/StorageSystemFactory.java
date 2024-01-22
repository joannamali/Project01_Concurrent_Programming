/*
 * University of Warsaw
 * Concurrent Programming Course 2023/2024
 * Java Assignment
 *
 * Author: Konrad Iwanicki (iwanicki@mimuw.edu.pl)
 */
package cp2023.solution;

import java.util.Map;

import cp2023.base.*;


public final class StorageSystemFactory {

    public static StorageSystem newSystem(
        Map<DeviceId, Integer> deviceTotalSlots,
        Map<ComponentId, DeviceId> componentPlacement) {

        StorageSystem S = new MyStorage(deviceTotalSlots, componentPlacement); 
        return S; 
    }

}

