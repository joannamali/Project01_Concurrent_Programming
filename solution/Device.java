package cp2023.solution;

import cp2023.base.*;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Device {
    public Map<ComponentTransfer, Condition> WaitingRoom = new HashMap<>(); 
    public DeviceId id; 
    public ConcurrentHashMap<ComponentId, DeviceId> AllComponents;
    public LinkedList<ComponentTransfer> WaitingLine = new LinkedList<>();
    public int freeSlots;
    public LinkedList<ComponentTransfer> occupiedSlots = new LinkedList<>();
    public Map<ComponentTransfer, ComponentTransfer> reservations = new HashMap<>(); 
    final ReentrantLock lock = new ReentrantLock(true);


    public Device(DeviceId id, int totalSlots, Map<DeviceId, Device> 
        DeviceMap, ConcurrentHashMap<ComponentId, DeviceId> AllComponents) {
        this.id = id; 
        this.AllComponents = AllComponents;
        freeSlots = totalSlots;   
    }

    // Checks if there are free slots or if it is possible to make reservation.
    public boolean TransferAdd(ComponentTransfer T) throws InterruptedException{
        WaitingRoom.put(T, lock.newCondition());
        if (WaitingLine.isEmpty()){
            if (freeSlots > 0){
                freeSlots--; 
                return false;
            }
            else if (!occupiedSlots.isEmpty()){
                ComponentTransfer occupying = occupiedSlots.poll(); 
                reservations.put(occupying, T); 
                return false;
            }
            else { 
                WaitingLine.add(T);
                return true;
            }  
        }
        else {
            WaitingLine.add(T);
            return true; 
        }
    }
    // If cycle was not found, transfer waits in the Waiting Room.
    public void AfterCycleCheck(ComponentTransfer T) throws InterruptedException {        
        while (WaitingLine.contains(T)){
            WaitingRoom.get(T).await();
        }
    }
    // Checks reservations and wakes up a waiting transfer or frees the slot.
    public void TransferLeave(ComponentTransfer T){
        if (reservations.containsKey(T)){
            ComponentTransfer getter = reservations.get(T); 
            reservations.remove(T); 
            WaitingRoom.get(getter).signal();
        }
        else {
            occupiedSlots.remove(T); 
            freeSlots++; 
        }
    }
    // Waits for preview transfer to end his prepare() and changes 
    // its current device.
    public void TransferEnter(ComponentTransfer T) throws InterruptedException{
        while (reservations.containsValue(T))
            WaitingRoom.get(T).await();
        ComponentId componentId = T.getComponentId();
        AllComponents.replace(componentId, this.id);
        AllComponents.putIfAbsent(componentId, this.id);
        WaitingRoom.remove(T);
    }
}
